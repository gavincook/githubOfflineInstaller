package com.github.installer;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * @author GavinCook
 * @date 2015/12/2
 */
public class Fetcher {

    /**
     * 版本号
     */
    private String version = "3_0_9_0";

    /**
     * 下载github离线安装包所需文件的地址
     */
    private String baseUrl = "https://github-windows.s3.amazonaws.com/%s";

    private String applicationBaseUrl = "https://github-windows.s3.amazonaws.com/Application Files/GitHub_%s/%s";

    /**
     * 最终需要制作安装包的路径, 运行程序的时候从命令行输入即可
     */
    private String baseDir;

    /**
     * github安装包的application files文件夹路径
     */
    private String applicationFilesDir;

    /**
     * total 表示总的需要下载的文件，不包括GitHub.application和GitHub.exe.manifest
     * index 表示当前正在下载的文件索引
     */
    private int total , index;

    private CloseableHttpClient client = getClient();

    /**
     * 分析并下载github
     * @param onlyPrint 是否只打印下载路径
     * @throws DocumentException
     * @throws IOException
     */
    public void analysisAndDownload(boolean onlyPrint) throws DocumentException, IOException {
        prepare();
        SAXReader reader = new SAXReader();
        Document document = reader.read(new File(applicationFilesDir, "GitHub.exe.manifest"));
        Element element = document.getRootElement();

        List<String> needDownloadFiles = new ArrayList<>();

        List<Element> fileElements = element.elements("file");

        fileElements.forEach(e->{
            needDownloadFiles.add(e.attribute("name").getValue());
        });

        List<Element> dependencyElements = element.elements("dependency");

        dependencyElements.forEach(e->{
            e = e.element("dependentAssembly");
            if(e == null || e.attribute("codebase") == null) return;
            needDownloadFiles.add(e.attribute("codebase").getValue());
        });

        total = needDownloadFiles.size();

        needDownloadFiles.forEach(fileName-> {
            index++;
            download(fileName, true, onlyPrint);
        });

        client.close();
    }

    public static void main(String[] args) throws IOException, DocumentException {
        //下载github相关文件到输出目录
        new Fetcher().analysisAndDownload(false);

        //当网速不给力的时候，可以使用只打印下载路径，然后用下载工具，迅雷什么的，来完成下载，然后拷贝到对应目录即可
        //new Fetcher().analysisAndDownload(true);
    }


    /**
     * 下载准备，创建必要的文件结构，以及下载github安装包基础文件
     * @throws IOException
     */
    private void prepare() throws IOException {
        System.out.println("Current GitHub version is "+version.replaceAll("_",".")+", Please enter the output directory path: ");
        Scanner scanner = new Scanner(System.in);
        baseDir = scanner.nextLine();
        File out = new File(baseDir, "Application Files/GitHub_"+version);
        out.mkdirs();
        applicationFilesDir = out.getAbsolutePath();
        download("GitHub.application", false, false);
        download("GitHub.exe.manifest", true, false);
    }

    /**
     * 获取下载文件的真实名字，因为所有application files下除了manifest文件外都需要加后缀.deploy
     */
    private String getActualName(String fileName){
        if(!fileName.endsWith("manifest")){
            fileName = fileName + ".deploy";
        }
        return fileName;
    }

    /**
     * 下载指定文件
     * @param fileName 需要下载的文件名字
     * @param applicationFiles 是否是application files下的文件
     * @param onlyPrint 是否只是打印下载路径，如果为true,则表示只打印下载路径，不会下载文件
     */
    private void download(String fileName, boolean applicationFiles, boolean onlyPrint){
        String url;
        File dist;
        if(applicationFiles){
            fileName = getActualName(fileName);
            url = String.format(applicationBaseUrl, version, fileName).replaceAll("\\s", "%20").replaceAll("\\\\", "/");
            dist = new File(applicationFilesDir, fileName);
        }else{
            url = String.format(baseUrl, fileName);
            dist = new File(baseDir, fileName);
        }

        dist.getParentFile().mkdirs();

        if(onlyPrint){
            System.out.println(url);
            return;
        }

        if(dist.exists() && dist.getTotalSpace() > 0) return;


        System.out.print("Downloading ");
        if(total > 0 && index > 0){
            System.out.print("["+index+"/"+total+"] ");
        }
        System.out.print(fileName+" from "+ url);

        HttpGet request = new HttpGet(url);
        try {
            CloseableHttpResponse response = client.execute(request);
            System.out.println(" , Total " + response.getFirstHeader("Content-Length").getValue() + " bytes");

            save(response.getEntity().getContent(), dist);
            System.out.println("Success to Downloaded " + fileName+" to "+dist.getAbsolutePath());
            request.releaseConnection();
            response.close();
        }catch (IOException e){
            dist.deleteOnExit();
            System.out.println("\nFailed to Downloaded " + fileName+", continues next.");
        }
    }

    /**
     * 保存文件到指定位置
     * @throws IOException
     */
    private void save(InputStream in, File file) throws IOException {
        try(OutputStream out = new FileOutputStream(file)){
            byte[] data = new byte[10240];
            int length;
            while((length = in.read(data)) != -1){
                out.write(data, 0, length);
            }
        }finally {
            in.close();
        }
    }

    /**
     * 一个不需要验证证书的ssl client
     */
    public CloseableHttpClient getClient(){
        try {
            HttpClientBuilder builder = HttpClientBuilder.create();
            SSLContext context = SSLContext.getInstance("TLS");
            X509TrustManager tm = new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException { }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException { }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
            };
            context.init(null, new TrustManager[]{tm}, null);
            builder.setSslcontext(context);
            return builder.build();
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }
}
