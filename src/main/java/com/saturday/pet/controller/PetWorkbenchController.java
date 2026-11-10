package com.saturday.pet.controller;

import com.saturday.pet.app.PetWorkbenchService;
import com.saturday.pet.app.PetWorkbenchService.ArtifactReceipt;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/pet/workbench")
public class PetWorkbenchController {
    private static final int MAX_MESSAGE_LENGTH = 4000;

    private final PetWorkbenchService workbench;

    public PetWorkbenchController(PetWorkbenchService workbench) {
        this.workbench = workbench;
    }

    public record MessageRequest(String message, String chatId) {}
    public record ConfirmRequest(String chatId) {}
    public record ProgressEvent(String label) {}
    public record ErrorEvent(String message) {}

    @PostMapping(value = "/messages", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<ServerSentEvent<Object>>> sendMessage(@RequestBody MessageRequest request) {
        return stream(request.message(), request.chatId(), null);
    }

    @PostMapping(value = "/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<ServerSentEvent<Object>>> sendImageMessage(@RequestParam String message,
            @RequestParam String chatId, @RequestPart("image") MultipartFile image) {
        return stream(message, chatId, image);
    }

    private ResponseEntity<Flux<ServerSentEvent<Object>>> stream(String message, String chatId, MultipartFile image) {
        if (message == null || message.isBlank() || message.length() > MAX_MESSAGE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入 1 到 4000 字的消息");
        }
        UUID session = parseChatId(chatId);
        var events = Flux.<ServerSentEvent<Object>>create(sink -> {
            try {
                var result = workbench.run(message, session, image,
                        label -> sink.next(event("progress", new ProgressEvent(label))),
                        step -> sink.next(event("step", step)), sink::isCancelled);
                sink.next(event("result", result));
            } catch (CancellationException cancelled) {
                // The browser disconnected. Leave any unconfirmed actions untouched.
            } catch (Exception exception) {
                if (!sink.isCancelled()) {
                    sink.next(event("error", new ErrorEvent(publicError(exception, image != null))));
                }
            } finally {
                sink.complete();
            }
        }).subscribeOn(Schedulers.boundedElastic());
        return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM)
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-transform")
                .header("X-Accel-Buffering", "no").body(events);
    }

    @PostMapping("/actions/{actionId}/confirm")
    public ResponseEntity<ArtifactReceipt> confirm(@PathVariable UUID actionId, @RequestBody ConfirmRequest request) {
        try {
            var receipt = workbench.confirm(actionId, parseChatId(request.chatId()));
            return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(receipt);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "暂时无法生成 PDF，请重试");
        }
    }

    @GetMapping("/artifacts/{id}")
    public ResponseEntity<byte[]> download(@PathVariable UUID id, @RequestHeader("X-Pet-Chat-Id") String chatId) {
        var artifact = workbench.artifact(id, parseChatId(chatId));
        String title = artifact.receipt().title().replaceAll("[\\p{Cc}\\\\/:*?\"<>|]", "-");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(title + ".pdf", StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff").body(artifact.pdf());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorEvent> requestError(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ErrorEvent(exception.getReason()));
    }

    private static UUID parseChatId(String value) {
        if (value != null && value.length() == 36) {
            try {
                UUID id = UUID.fromString(value);
                if (id.toString().equalsIgnoreCase(value)) {
                    return id;
                }
            } catch (IllegalArgumentException ignored) {
                // Map all invalid inputs to the same request error.
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会话标识无效");
    }

    private static ServerSentEvent<Object> event(String name, Object data) {
        return ServerSentEvent.builder(data).event(name).build();
    }

    private static String publicError(Exception exception, boolean hasImage) {
        if (exception instanceof ResponseStatusException status) {
            if (status.getStatusCode().value() == 429) {
                return "暂时无法保留更多清单，请稍后再试";
            }
            if (hasImage) {
                return switch (status.getStatusCode().value()) {
                    case 400 -> "图片无法读取，或像素过大，请换一张图片";
                    case 413 -> "图片不能超过 5 MB";
                    case 415 -> "图片仅支持 JPEG 和 PNG";
                    case 502 -> status.getReason();
                    case 503 -> "图片识别暂不可用，请稍后再试";
                    default -> "这次请求未完成，请稍后再试";
                };
            }
        }
        return "这次请求未完成，请稍后再试";
    }
}
