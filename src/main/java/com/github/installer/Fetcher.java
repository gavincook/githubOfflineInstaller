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
import java.io.*;
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
    private String version;

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

    private CloseableHttpClient client = getClient();

    //下载计数器
    private Counter counter = new Counter();

    //工作模式，默认下载
    private Mode mode = Mode.DOWNLOAD;

    private final String GITHUB_STARTUP_APPLICATION = "GitHub.application";

    private final String GITHUB_MANIFEST = "GitHub.exe.manifest";

    private List<String> needDownloadFiles = new ArrayList<>();

    //入口
    public static void main(String[] args) throws IOException, DocumentException {
        //下载github相关文件到输出目录
        new Fetcher().fetch();
        //当网速不给力的时候，可以使用只打印下载路径，然后用下载工具，迅雷什么的，来完成下载，然后拷贝到对应目录即可
        //只需要切换工作模式为Mode.SHOW_URL;即可
    }

    /**
     * 解析GitHub.exe.manifest，分析并下载github相关文件
     * @throws DocumentException
     * @throws IOException
     */
    private void fetch() throws DocumentException, IOException {
        prepare();
        counter.total(needDownloadFiles.size());
        needDownloadFiles.forEach(fileName-> {
            counter.increase();
            fetchFile(fileName);
        });

        client.close();
    }

    /**
     * 下载前准备，获取当前github版本，创建必要的文件结构，以及下载github安装包基础文件
     * @throws IOException
     */
    private void prepare() throws IOException, DocumentException {

        if(mode == Mode.SHOW_URL) {
            System.out.println("Directory  /");
            //分析启动入口，获取版本号
            fetchFile(GITHUB_STARTUP_APPLICATION, new Handler() {
                @Override
                public boolean preHandle(String url) {
                    System.out.println("  " + url);
                    return true;
                }

                @Override
                public void postHandle(CloseableHttpResponse response) throws IOException {
                    fetchVersion(response.getEntity().getContent());

                }
            });
            System.out.println("Directory  /Application Files/GitHub_" + version);
            //分析manifest，获取需要下载的文件
            fetchFile(GITHUB_MANIFEST, new Handler() {
                @Override
                public boolean preHandle(String url) {
                    System.out.println("  " + url);
                    return true;
                }

                @Override
                public void postHandle(CloseableHttpResponse response) throws IOException {
                    analysisManifest(response.getEntity().getContent());
                }
            });

        }else{
            //创建必要的目录结构
            System.out.println("Please enter the output directory path: ");
            Scanner scanner = new Scanner(System.in);
            baseDir = scanner.nextLine();
            //下载Github启动文件，并分析版本
            fetchFile(GITHUB_STARTUP_APPLICATION);
            fetchVersion(new FileInputStream(new File(baseDir, GITHUB_STARTUP_APPLICATION)));
            System.out.println("Current GitHub version is " + version.replaceAll("_","."));

            File out = new File(baseDir, "Application Files/GitHub_"+version);
            out.mkdirs();
            this.applicationFilesDir = out.getAbsolutePath();

            //下载github对应版本的相关文件以及相关依赖文件
            fetchFile(GITHUB_MANIFEST);
            analysisManifest(new FileInputStream(new File(applicationFilesDir, "GitHub.exe.manifest")));

        }
    }

    /**
     * 分析需要下载的文件
     * @param in
     */
    private void analysisManifest(InputStream in)  {
        try {
            SAXReader reader = new SAXReader();
            Document document = reader.read(in);
            Element element = document.getRootElement();
            in.close();
            List<Element> fileElements = element.elements("file");

            fileElements.forEach(e -> {
                needDownloadFiles.add(e.attribute("name").getValue());
            });

            List<Element> dependencyElements = element.elements("dependency");

            dependencyElements.forEach(e -> {
                e = e.element("dependentAssembly");
                if (e == null || e.attribute("codebase") == null) return;
                needDownloadFiles.add(e.attribute("codebase").getValue());
            });
        }catch (Exception e){
            throw new IllegalStateException("Can't analysis manifest for github");
        }
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
     * 下载指定文件或者打印文件的地址，视工作模式而定
     * @param fileName 需要下载的文件名字
     */
    private void fetchFile(String fileName){
        String url;
        File dist;
        final String actualFileName;
        if(!GITHUB_STARTUP_APPLICATION.equals(fileName)){
            actualFileName = getActualName(fileName);
            url = String.format(applicationBaseUrl, version, actualFileName).replaceAll("\\s", "%20").replaceAll("\\\\", "/");
            dist = new File(applicationFilesDir, actualFileName);
        }else{
            actualFileName = fileName;
            url = String.format(baseUrl, actualFileName);
            dist = new File(baseDir, actualFileName);
        }

        if(mode == Mode.SHOW_URL){
            System.out.println("  " + url);
            return;
        }

        dist.getParentFile().mkdirs();
        //如果文件存在，并且文件大小不为零，则忽略
        if(dist.exists() && dist.length() > 0) {
            System.out.println(counter.toString() + " The file " + actualFileName +" is existed, ignore it.");
            return;
        }

        //下载文件
        fetchFile(fileName, new Handler() {
            @Override
            public boolean preHandle(String url) {
                System.out.print("Downloading " + counter.toString() + " " +actualFileName + " from "+ url);
                return true;
            }

            @Override
            public void postHandle(CloseableHttpResponse response) {
                try {
                    System.out.println(" , Total " + response.getFirstHeader("Content-Length").getValue() + " bytes");
                    save(response.getEntity().getContent(), dist);
                    System.out.println("Success to Downloaded " + actualFileName+" to "+dist.getAbsolutePath());
                }catch (NullPointerException e) {
                    System.out.println("Failed to get the file size, continue. ");
                }catch (IOException e) {
                    System.out.println("\nFailed to Downloaded " + actualFileName+", continues next.");
                }
            }
        });
    }

    /**
     * 提取远端文件，并使用处理器嵌入前后置处理逻辑
     * @param fileName
     * @param handler
     */
    private void fetchFile(String fileName, Handler handler){
        String url;
        if(!GITHUB_STARTUP_APPLICATION.equals(fileName)){
            fileName = getActualName(fileName);
            url = String.format(applicationBaseUrl, version, fileName).replaceAll("\\s", "%20").replaceAll("\\\\", "/");
        }else{
            url = String.format(baseUrl, fileName);
        }

        if(!handler.preHandle(url)){
            return;
        }

        HttpGet request = new HttpGet(url);
        try {
            CloseableHttpResponse response = client.execute(request);
            handler.postHandle(response);
            request.releaseConnection();
            response.close();
        }catch (IOException e){
            System.out.println("\nFailed to fetch " + fileName+", continues next.");
        }
    }

    /**
     * 获取当前的版本号
     * @return
     * @throws DocumentException
     */
    private void fetchVersion(InputStream in){
        try {
            SAXReader reader = new SAXReader();
            Document document = reader.read(in);
            in.close();

            Element rootElement = document.getRootElement();

            List<Element> assemblyElements = rootElement.elements("assemblyIdentity");

            if (assemblyElements.size() == 0) {
                throw new IllegalStateException("Can't get the github version from Github.application");
            }

            for (Element element : assemblyElements) {
                if ("GitHub.application".equals(element.attributeValue("name"))) {
                    String version = element.attributeValue("version");
                    if (version != null) {
                        this.version = version.replaceAll("\\.", "_");//将版本号变成下划线连接，如3.1.1.4-->3_1_1_4
                        return;
                    }
                }
            }
        }catch (Exception e){
            e.printStackTrace();
            throw new IllegalStateException("Can't get the github version from Github.application");
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
    private CloseableHttpClient getClient(){
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

    /**
     * 下载计数器
     * total 表示总的需要下载的文件，不包括GitHub.application和GitHub.exe.manifest
     * index 表示当前正在下载的文件索引
     */
    private class Counter{
        private int index;

        private int total;

        public Counter(){}

        public void total(int total){
            this.total = total;
        }

        public void increase(){
            index++ ;
        }

        @Override
        public String toString() {
            if(index > 0 && total > 0) {
                return "[" + index + "/" + total + "]";
            }else{
                return "";
            }
        }
    }

    /**
     * 提取模式
     */
    private enum Mode{
        DOWNLOAD,//下载相关文件到指定目录
        SHOW_URL;//仅仅将需要下载的文件路径打印出来
    }

    /**
     * 提取远端文件的前后置处理器
     */
    private interface Handler{

        boolean preHandle(String url);

        void postHandle(CloseableHttpResponse response) throws IOException;
    }
}