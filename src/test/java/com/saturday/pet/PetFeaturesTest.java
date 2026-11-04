package com.saturday.pet;
import com.saturday.pet.app.PetApp;
import com.saturday.pet.advisor.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import java.nio.file.Path;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.*;
class PetFeaturesTest {
 @TempDir Path directory;
 private ChatModel model(String text){
  ChatModel model=mock(ChatModel.class);
  when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
  return model;
 }
 @Test void convertsReportAndRejectsIncompleteOutput(){
  PetApp app=new PetApp(model("{\"title\":\"Care\",\"petName\":\"Milo\",\"suggestions\":[\"Fresh water\"]}"));
  assertThat(app.doChatWithReport("report","a").petName()).isEqualTo("Milo");
  assertThatThrownBy(()->new PetApp(model("{}")).doChatWithReport("report","b")).isInstanceOf(IllegalStateException.class);
 }
 @Test void advisorsModifyRequestAndPreserveResponse(){
  ChatModel model=model("answer");
  String answer=ChatClient.builder(model).defaultAdvisors(new MyLoggerAdvisor(),new ReReadingAdvisor()).build().prompt().user("hello").call().content();
  assertThat(answer).isEqualTo("answer");
  var captor=org.mockito.ArgumentCaptor.forClass(Prompt.class);
  verify(model).call(captor.capture());
  assertThat(captor.getValue().getUserMessage().getText()).isEqualTo("hello\nRead the question again: hello");
 }
 @Test void fileDatabaseSurvivesRepositoryRecreationAndDeletion(){
  String url="jdbc:hsqldb:file:"+directory.resolve("memory").toAbsolutePath();
  var ds=new DriverManagerDataSource(url,"sa","");
  new ResourceDatabasePopulator(new ClassPathResource("org/springframework/ai/chat/memory/repository/jdbc/schema-hsqldb.sql")).execute(ds);
  var first=JdbcChatMemoryRepository.builder().jdbcTemplate(new JdbcTemplate(ds)).build();
  first.saveAll("a",List.of(new UserMessage("Milo"),new AssistantMessage("hello")));
  new JdbcTemplate(ds).execute("SHUTDOWN");
  var second=JdbcChatMemoryRepository.builder().jdbcTemplate(new JdbcTemplate(new DriverManagerDataSource(url,"sa",""))).build();
  assertThat(second.findByConversationId("a")).hasSize(2);
  assertThat(second.findByConversationId("b")).isEmpty();
  second.deleteByConversationId("a");
  assertThat(first.findByConversationId("a")).isEmpty();
  new JdbcTemplate(ds).execute("SHUTDOWN");
 }
}
