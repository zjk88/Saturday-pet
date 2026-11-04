package com.saturday.pet.controller;

import com.saturday.pet.app.PetVisionApp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController
@RequestMapping("/pet")
@Tag(name = "宠物图片理解")
public class PetVisionController {

    private final PetVisionApp app;

    public PetVisionController(PetVisionApp app) {
        this.app = app;
    }

    @PostMapping(value = "/image/explain", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传宠物图片并提问（单次图片理解）")
    public String explain(@RequestParam(defaultValue = "请描述这张宠物图片") String message,
                          @RequestPart("image") MultipartFile image) throws IOException {
        return new com.saturday.pet.app.PetImageService(app).explain(message, image);
    }
}
