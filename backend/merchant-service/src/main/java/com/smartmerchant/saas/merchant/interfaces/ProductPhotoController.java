package com.smartmerchant.saas.merchant.interfaces;

import com.smartmerchant.saas.security.TenantContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.*;
import java.util.*;

@RestController
public class ProductPhotoController {
    private final JdbcTemplate jdbc;
    public ProductPhotoController(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @PostMapping("/api/merchant/v1/product-photos")
    @PreAuthorize("hasAuthority('merchant:product:manage')")
    public Map<String,String> upload(@RequestParam("file") MultipartFile file) throws IOException {
        var context=TenantContextHolder.require();
        if(!"TENANT_ALL".equals(context.dataScope()))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if(file.isEmpty()||file.getSize()>1024*1024)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"照片大小需在 1 MB 以内");
        byte[] bytes=file.getBytes();
        String mime;
        try(var input=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请选择 JPEG 或 PNG 照片");
            var reader=readers.next();
            try {
                reader.setInput(input);
                String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                if(!Set.of("jpeg","png").contains(format)||reader.getWidth(0)>6000||reader.getHeight(0)>6000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请选择尺寸不超过 6000 像素的 JPEG 或 PNG 照片");
                if(reader.read(0)==null)throw new IOException("Invalid image");
                mime="png".equals(format)?"image/png":"image/jpeg";
            } finally { reader.dispose(); }
        } catch(IOException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"照片无法读取，请重新选择"); }
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO product_photo(id,tenant_id,content_type,content) VALUES (?,?,?,?)",id,context.tenantId(),mime,bytes);
        return Map.of("url","/api/consumer/v1/catalog/tenants/"+context.tenantId()+"/photos/"+id);
    }
    @GetMapping("/api/consumer/v1/catalog/tenants/{tenantId}/photos/{id}")
    public ResponseEntity<byte[]> photo(@PathVariable long tenantId,@PathVariable String id) {
        var rows=jdbc.query("SELECT content_type,content FROM product_photo WHERE tenant_id=? AND id=?",(r,n)->ResponseEntity.ok().contentType(MediaType.parseMediaType(r.getString(1))).header("X-Content-Type-Options","nosniff").header("Cache-Control","public, max-age=86400").body(r.getBytes(2)),tenantId,id);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return rows.getFirst();
    }
}
