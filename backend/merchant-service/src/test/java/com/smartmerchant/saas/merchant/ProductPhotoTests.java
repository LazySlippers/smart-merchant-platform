package com.smartmerchant.saas.merchant;
import com.smartmerchant.saas.merchant.interfaces.ProductPhotoController;
import com.smartmerchant.saas.security.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(properties="spring.cloud.nacos.discovery.enabled=false")
class ProductPhotoTests {
 @Autowired JdbcTemplate jdbc;
 ProductPhotoController controller;
 @BeforeEach void setup(){controller=new ProductPhotoController(jdbc);TenantContextHolder.set(new TenantContext(991,1,false,"TENANT_ALL",Set.of()));}
 @AfterEach void cleanup(){TenantContextHolder.clear();jdbc.update("DELETE FROM product_photo WHERE tenant_id=991");}
 @Test void realPhotoPersistsAndCannotBeReadThroughAnotherTenant() throws Exception {
  var output=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",output);
  var result=controller.upload(new MockMultipartFile("file","photo.png","image/png",output.toByteArray()));
  var id=result.get("url").substring(result.get("url").lastIndexOf('/')+1);
  assertThat(controller.photo(991,id).getBody()).isEqualTo(output.toByteArray());
  assertThatThrownBy(()->controller.photo(992,id)).isInstanceOf(ResponseStatusException.class);
 }
 @Test void fakeAndOversizedFilesAreRejected(){
  assertThatThrownBy(()->controller.upload(new MockMultipartFile("file","fake.png","image/png","not a photo".getBytes()))).isInstanceOf(ResponseStatusException.class);
  assertThatThrownBy(()->controller.upload(new MockMultipartFile("file","large.png","image/png",new byte[1024*1024+1]))).isInstanceOf(ResponseStatusException.class);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_photo WHERE tenant_id=991",Long.class)).isZero();
 }
}
