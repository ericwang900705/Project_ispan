package gameplatform.publisher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import gameplatform.support.controller.*;
import gameplatform.support.config.StaffPermissionService;
import gameplatform.support.model.*;
import gameplatform.support.service.*;
import java.nio.file.*;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.MethodParameter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.*;

/** 實際本機檔案與 Spring 路由測試；不接觸使用者資料庫。 */
class PublisherFilesTests {
 @TempDir Path dir;
 PublisherFileStorage storage;
 JdbcTemplate db;
 StaffPermissionService permissions;
 MockMvc mvc;
 Actor actor = new Actor(7,"PUBLISHER","publisher","");
 @BeforeEach void setup() {
  storage=new PublisherFileStorage(dir.toString()); db=mock(JdbcTemplate.class); permissions=mock(StaffPermissionService.class);
  var resolver=new HandlerMethodArgumentResolver() {
   public boolean supportsParameter(MethodParameter p) { return p.getParameterType()==Actor.class; }
   public Object resolveArgument(MethodParameter p,ModelAndViewContainer m,NativeWebRequest r,WebDataBinderFactory b){return actor;}
  };
  mvc=MockMvcBuilders.standaloneSetup(new PublisherFileController(storage,db,permissions),new NotificationController(mock(NotificationService.class)))
   .setCustomArgumentResolvers(resolver).setControllerAdvice(new Problem.Handler()).build();
 }
 byte[] zip() throws Exception {
  var bytes=new ByteArrayOutputStream();
  try(var zip=new ZipOutputStream(bytes)){zip.putNextEntry(new ZipEntry("demo/index.html"));zip.write("game demo".getBytes());zip.closeEntry();}
  return bytes.toByteArray();
 }
 Map<String,Object> uploadZip() throws Exception {return storage.upload(actor,new MockMultipartFile("file","game.zip","application/zip",zip()),"BUILD");}
 @Test void zipUploadStoresExactBytesAndOwnerScopedUrl() throws Exception {
  var result=uploadZip();String url=result.get("url").toString();
  assertTrue(PublisherFileStorage.isBuildUrl(url,7));assertFalse(PublisherFileStorage.isBuildUrl(url,8));
  assertEquals((long)zip().length,result.get("fileSize"));
  assertArrayEquals(zip(),Files.readAllBytes(storage.resolve("builds",7,url.substring(url.lastIndexOf('/')+1))));
 }
 @Test void mp4UploadHasPublicVideoUrl() {
  var result=storage.upload(actor,new MockMultipartFile("file","trailer.mp4","video/mp4",new byte[]{0,0,0,24,'f','t','y','p','i','s','o','m'}),"VIDEO");
  assertTrue(PublisherFileStorage.isVideoUrl(result.get("url").toString()));
 }
 @Test void rejectsOtherRoles() {
  var e=assertThrows(Problem.class,()->storage.upload(new Actor(9,"MEMBER","m",""),new MockMultipartFile("file","trailer.mp4","video/mp4",new byte[12]),"VIDEO"));assertEquals(403,e.httpStatus());
 }
 @Test void rejectsSpoofedVideoAndCleansUp() throws Exception {
  assertThrows(Problem.class,()->storage.upload(actor,new MockMultipartFile("file","fake.mp4","video/mp4","not a video".getBytes()),"VIDEO"));
  try(var paths=Files.walk(dir)){assertEquals(0,paths.filter(Files::isRegularFile).count());}
 }
 @Test void rejectsCorruptZipAndWrongExtension() {
  assertThrows(Problem.class,()->storage.upload(actor,new MockMultipartFile("file","fake.zip","application/zip","broken".getBytes()),"BUILD"));
  assertThrows(Problem.class,()->storage.upload(actor,new MockMultipartFile("file","fake.exe","application/zip",new byte[]{1}),"BUILD"));
 }
 @Test void rejectsEmptyAndOversizedFiles() {
  assertThrows(Problem.class,()->storage.upload(actor,new MockMultipartFile("file","empty.zip","application/zip",new byte[0]),"BUILD"));
  var big=new MockMultipartFile("file","big.zip","application/zip",new byte[]{1}) { @Override public long getSize(){return PublisherFileStorage.MAX_BYTES+1;} };
  assertEquals(400,assertThrows(Problem.class,()->storage.upload(actor,big,"BUILD")).httpStatus());
 }
 @Test void rejectsTraversalNames() {assertThrows(Problem.class,()->storage.resolve("builds",7,"../secret.zip"));}
 @Test void ownerDownloadsZipWithNoStore() throws Exception {
  String url=uploadZip().get("url").toString();mvc.perform(get(url)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(content().bytes(zip()));verifyNoInteractions(db);
 }
 @Test void otherPublisherCannotDownloadPrivateZip() throws Exception {
  String url=uploadZip().get("url").toString();actor=new Actor(8,"PUBLISHER","other","");
  when(db.queryForObject(anyString(),eq(Integer.class),eq(7),eq(url),eq(8))).thenReturn(0);
  mvc.perform(get(url)).andExpect(status().isNotFound());
 }
 @Test void staffWithoutGameReviewCannotDownload() throws Exception {
  String url=uploadZip().get("url").toString();actor=new Actor(8,"STAFF","staff","");when(permissions.forActor(actor)).thenReturn(List.of());
  mvc.perform(get(url)).andExpect(status().isNotFound());verifyNoInteractions(db);
 }
 @Test void reviewerCanDownloadSubmittedBuild() throws Exception {
  String url=uploadZip().get("url").toString();actor=new Actor(8,"REVIEWER","r","");when(permissions.forActor(actor)).thenReturn(List.of("GAME_REVIEW"));
  when(db.queryForObject(anyString(),eq(Integer.class),eq(7),eq(url))).thenReturn(1);
  mvc.perform(get(url)).andExpect(status().isOk());
 }
 @Test void memberWithActiveLibraryEntryCanDownload() throws Exception {
  String url=uploadZip().get("url").toString();actor=new Actor(8,"MEMBER","m","");
  when(db.queryForObject(anyString(),eq(Integer.class),eq(7),eq(url),eq(8))).thenReturn(1);
  mvc.perform(get(url)).andExpect(status().isOk());
 }
 @Test void readEndpointsExplicitlyReturnNoContent() throws Exception {
  mvc.perform(post("/api/notifications/read-all")).andExpect(status().isNoContent()).andExpect(content().string(""));
  mvc.perform(post("/api/notifications/12/read")).andExpect(status().isNoContent());
 }
 @Test void multipartZipUploadUsesSameEndpointAsFrontend() throws Exception {
  mvc.perform(multipart("/api/publisher/files").file(new MockMultipartFile("file","demo.zip","application/zip",zip())).param("kind","BUILD"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.type").value("BUILD")).andExpect(jsonPath("$.url").exists());
 }
}
