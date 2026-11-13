package dev.voidclient;

import com.google.gson.*;
import java.net.*;
import java.net.http.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.jar.JarFile;
import net.fabricmc.loader.api.FabricLoader;

final class Catalog {
    record Hit(String id,String title,String page){}
    record Release(String name,String url,String hash,String algorithm,int dependencies){}
    static final String MC="26.3", MR="https://api.modrinth.com/v2/", CF="https://api.curseforge.com/v1/";
    static final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build();
    static String enc(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
    static JsonElement json(String url)throws Exception{
        var b=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(25)).header("User-Agent","VoidClient/1.0 (Fabric mod browser)");
        if(url.startsWith(CF)){String key=System.getenv("VOID_CURSEFORGE_KEY");if(key==null||key.isBlank())throw new IOException("Set VOID_CURSEFORGE_KEY or use the website.");b.header("x-api-key",key);}
        var r=http.send(b.build(),HttpResponse.BodyHandlers.ofString());
        if(r.statusCode()!=200)throw new IOException("Catalog returned HTTP "+r.statusCode());return JsonParser.parseString(r.body());
    }
    static String str(JsonObject o,String k){return o.has(k)&&!o.get(k).isJsonNull()?o.get(k).getAsString():"";}
    static List<Hit> search(boolean cf,String q,int offset)throws Exception{
        var list=new ArrayList<Hit>();String url=cf?CF+"mods/search?gameId=432&classId=6&gameVersion="+MC+"&modLoaderType=4&pageSize=12&index="+offset+"&searchFilter="+enc(q):
        MR+"search?limit=12&offset="+offset+"&query="+enc(q)+"&facets="+enc("[[\"project_type:mod\"],[\"categories:fabric\"],[\"versions:26.3\"],[\"client_side:required\",\"client_side:optional\"]]");
        for(var v:json(url).getAsJsonObject().getAsJsonArray(cf?"data":"hits")){var o=v.getAsJsonObject();list.add(new Hit(str(o,cf?"id":"project_id"),str(o,cf?"name":"title"),(cf?"https://www.curseforge.com/minecraft/mc-mods/":"https://modrinth.com/mod/")+enc(str(o,"slug"))));}return list;
    }
    static Release resolve(boolean cf,Hit hit)throws Exception{
        var versions=cf?json(CF+"mods/"+hit.id+"/files?gameVersion="+MC+"&modLoaderType=4&pageSize=50").getAsJsonObject().getAsJsonArray("data"):
        json(MR+"project/"+hit.id+"/version?loaders="+enc("[\"fabric\"]")+"&game_versions="+enc("[\""+MC+"\"]")).getAsJsonArray();
        for(var v:versions){var o=v.getAsJsonObject();int deps=0;
            for(var d:o.getAsJsonArray("dependencies")){var obj=d.getAsJsonObject();if(cf?obj.get("relationType").getAsInt()==3:str(obj,"dependency_type").equals("required"))deps++;}
            if(cf){if(o.get("releaseType").getAsInt()!=1)continue;String url=str(o,"downloadUrl");if(url.isBlank())throw new IOException("Author requires website download.");
                for(var h:o.getAsJsonArray("hashes")){var a=h.getAsJsonObject();if(a.get("algo").getAsInt()==1)return new Release(str(o,"fileName"),url,str(a,"value"),"SHA-1",deps);}
            }else if("release".equals(str(o,"version_type"))){for(var f:o.getAsJsonArray("files")){var a=f.getAsJsonObject();if(a.get("primary").getAsBoolean()&&str(a,"filename").endsWith(".jar"))return new Release(str(a,"filename"),str(a,"url"),str(a.getAsJsonObject("hashes"),"sha512"),"SHA-512",deps);}}
        }throw new IOException("No compatible stable release found.");
    }
    static void install(Release f)throws Exception{
        URI u=URI.create(f.url);String host=u.getHost();
        if(!"https".equals(u.getScheme())||!("cdn.modrinth.com".equals(host)||"edge.forgecdn.net".equals(host)||"mediafilez.forgecdn.net".equals(host)||"media.forgecdn.net".equals(host)))throw new IOException("Unsupported download host.");
        if(!f.name.matches("[A-Za-z0-9_+().\\[\\] -][A-Za-z0-9_.+()\\[\\] -]*\\.jar"))throw new IOException("Unsupported file name.");
        Path dir=FabricLoader.getInstance().getGameDir().resolve("mods");Files.createDirectories(dir);Path target=dir.resolve(f.name);
        if(Files.exists(target))throw new IOException("This file is already installed.");
        Path tmp=Files.createTempFile(dir,"void-", ".part");try{
            HttpResponse<InputStream> r=null;
            for(int n=0;n<4;n++){r=http.send(HttpRequest.newBuilder(u).timeout(Duration.ofSeconds(90)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
                if(r.statusCode()==200)break;r.body().close();
                if(r.statusCode()/100!=3||n==3)throw new IOException("Download returned HTTP "+r.statusCode());
                u=u.resolve(r.headers().firstValue("location").orElseThrow());
                if(!"https".equals(u.getScheme())||u.getHost()==null||!u.getHost().endsWith(".forgecdn.net"))throw new IOException("Unsupported redirect.");
            }
            var digest=MessageDigest.getInstance(f.algorithm);
            try(var in=r.body();var out=Files.newOutputStream(tmp)){byte[] buf=new byte[32768];long size=0;int n;
                while((n=in.read(buf))!=-1){if((size+=n)>256L*1024*1024)throw new IOException("File exceeds 256 MiB.");out.write(buf,0,n);digest.update(buf,0,n);}}
            if(!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(f.hash))throw new IOException("Checksum mismatch.");
            try(var jar=new JarFile(tmp.toFile())){var e=jar.getJarEntry("fabric.mod.json");if(e==null)throw new IOException("Missing Fabric metadata.");
                try(var reader=new InputStreamReader(jar.getInputStream(e),StandardCharsets.UTF_8)){String id=str(JsonParser.parseReader(reader).getAsJsonObject(),"id");if(FabricLoader.getInstance().isModLoaded(id))throw new IOException("Mod already loaded. Update it in your launcher.");}}
            Files.move(tmp,target);
        }finally{Files.deleteIfExists(tmp);}
    }
}

