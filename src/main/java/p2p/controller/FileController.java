package p2p.controller;

import org.apache.commons.io.IOUtils;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import p2p.service.FileSharer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileController
{
    private final FileSharer fileSharer;
    private final HttpServer server;
    private final String uploadDir;
    private final ExecutorService executorService;

    public FileController(int port) throws IOException
    {
        this.fileSharer=new FileSharer();
        this.server=HttpServer.create(new InetSocketAddress(port),0);
        this.uploadDir=System.getProperty("java.io.tmpdir")+ File.separator + "ShareAll-uploads";
        this.executorService= Executors.newFixedThreadPool(10);

        File uploadDirFile=new File(uploadDir);
        if(!uploadDirFile.exists())
            uploadDirFile.mkdirs();

        server.createContext("/upload",new UploadHandler());
        server.createContext("/download",new DownloadHandler());
        server.createContext("/",new CORSHandler());
        server.setExecutor(executorService);

    }

    public void start()
    {
        server.start();
        System.out.println("API server started on port "+server.getAddress().getPort());
    }

    public void stop()
    {
        server.stop(0);
        executorService.shutdown();
        System.out.println("API Server Stopped.");
    }

    private class CORSHandler implements HttpHandler
    {
        @Override
        public void handle(HttpExchange exchange)throws IOException
        {
            Headers headers=exchange.getResponseHeaders();
            headers.add("Access-Control-Allow-Origin","*");
            headers.add("Access-Control-Allow-Methods","GET, POST, OPTIONS");
            headers.add("Access-Control-Allow-Headers","Content-Type, Authorization");

            if(exchange.getRequestMethod().equals("OPTIONS"))
            {
                exchange.sendResponseHeaders(204, -1);
                return ;
            }

            String response="NOT FOUND";
            exchange.sendResponseHeaders(404,response.getBytes().length);

            try(OutputStream oos=exchange.getResponseBody())
            {
                oos.write(response.getBytes());
                oos.close();
            }

        }
    }

    private class UploadHandler implements HttpHandler
    {
        @Override
        public void handle(HttpExchange exchange)throws IOException
        {
            Headers headers=exchange.getResponseHeaders();
            headers.add("Access-Control-Allow-Origin","*");
            if(!exchange.getRequestMethod().equalsIgnoreCase("POST"))
            {
                String response = "Method not allowed.";
                exchange.sendResponseHeaders(405,response.getBytes().length);
                try(OutputStream oos=exchange.getResponseBody())
                {
                    oos.write(response.getBytes());
                    oos.close();
                }
                return ;
            }
            Headers requaestHeaders=exchange.getRequestHeaders();
            String contentType=requaestHeaders.getFirst("Content-Type");
            if(contentType==null || contentType.equalsIgnoreCase("multipart/form-data"))
            {
                String response="Bad Request: Content-Type must be multipart/form-data";
                exchange.sendResponseHeaders(400,response.getBytes().length);
                try(OutputStream oos=exchange.getResponseBody())
                {
                    oos.write(response.getBytes());
                    oos.close();
                }
                return ;
            }
            try
            {
                String boundary=contentType.substring(contentType.indexOf("boundary=")+9);
                ByteArrayOutputStream baos=new ByteArrayOutputStream();

                IOUtils.copy(exchange.getRequestBody(),baos);
                byte[] requestData=baos.toByteArray();

                MultiParser parser=new MultiParser(requestData,boundary);
                MultiParser.ParseResult result=parser.parse();

                if(result==null)
                {
                    String response="Bad request: Unable to parse file content.";
                    exchange.sendResponseHeaders(400,response.getBytes().length);
                    try(OutputStream oos=exchange.getResponseBody())
                    {
                        oos.write(response.getBytes());
                    }
                    return ;
                }

                String fileName=result.fileName;
                if(fileName==null || fileName.trim().isEmpty())
                    fileName="unnamed-file";
                String uniqueFileName=UUID.randomUUID().toString()+"_"+new File(fileName).getName();
                String filePath=uploadDir+File.separator+uniqueFileName;

                try(FileOutputStream fos=new FileOutputStream(filePath))
                {
                    fos.write(result.fileContent);
                }

                int port=fileSharer.offerFile(filePath);
                new Thread(()-> fileSharer.startFileServer(port));
                String jsonResponse="{\"port\": "+port+"}";
                headers.add("Content-Type","applicat/json");
                exchange.sendResponseHeaders(200,jsonResponse.getBytes().length);
                try(OutputStream oos=exchange.getResponseBody())
                {
                    oos.write(jsonResponse.getBytes());
                }

            }
            catch (Exception e)
            {
                //throw new RuntimeException(e);
                System.err.println("Error processing file upload"+e.getMessage());
                String response="Server Error: "+e.getMessage();
                exchange.sendResponseHeaders(500,response.getBytes().length);
                try(OutputStream oos=exchange.getResponseBody())
                {
                    oos.write(response.getBytes());
                }
            }
        }
    }

    private static class MultiParser {
        private final byte[] data;
        private final String boundary;

        public MultiParser(byte[] data, String boundary) {
            this.data = data;
            this.boundary = boundary;
        }

        public ParseResult parse() {
            try {
                String dataAsString = new String(data);
                String fileNameMarker = "filename=\"";
                int fileNameStart = dataAsString.indexOf(fileNameMarker);
                if (fileNameStart == -1)
                    return null;
                int fileNameEnd = dataAsString.indexOf("\"", fileNameStart);
                String fileName = dataAsString.substring(fileNameStart, fileNameEnd);

                String contentTypeMarker = "Content-Type: ";
                int contentTypeStart = dataAsString.indexOf(contentTypeMarker, fileNameEnd);
                String contentType = "application/octet-stream";
                if (contentTypeStart != -1) {
                    contentTypeStart += contentTypeMarker.length();
                    int contentTypeEnd = dataAsString.indexOf("\r\n", contentTypeStart);
                    contentType = dataAsString.substring(contentTypeStart, contentTypeEnd);
                }

                String headerEndMarker = "\r\n\r\n";
                int headerEnd = dataAsString.indexOf(headerEndMarker);
                if (headerEnd == -1)
                    return null;

                int contentStart = headerEnd + headerEndMarker.length();

                byte[] boundaryBytes = ("\r\n--" + boundary + "--").getBytes();
                int contentEnd = findSequence(data, boundaryBytes, contentStart);

                if (contentEnd == -1) {
                    boundaryBytes = ("\r\n--" + boundary + "--").getBytes();
                    contentEnd = findSequence(data, boundaryBytes, contentStart);
                }

                if (contentEnd == -1 || contentEnd <= contentStart)
                    return null;

                byte[] fileContent = new byte[contentEnd - contentStart];
                System.arraycopy(data, contentStart, fileContent, 0, fileContent.length);
                return new ParseResult(fileName, fileContent, contentType);

            } catch (Exception e) {
                System.out.println("Error parsing data." + e.getMessage());
                return null;
            }
        }

        public static class ParseResult {
            public final String fileName;
            public final byte[] fileContent;
            public final String contentType;

            public ParseResult(String fileName, byte[] fileContent, String contentType) {
                this.fileName = fileName;
                this.fileContent = fileContent;
                this.contentType = contentType;
            }
        }

        public static int findSequence(byte[] data, byte[] sequence, int startPos) {
            outer:
            for (int i = startPos; i < data.length - sequence.length; ++i) {
                for (int j = 0; j < sequence.length; ++j) {
                    if (data[i + j] != sequence[j])
                        continue outer;
                }
                return i;
            }
            return -1;
        }
    }

    private class DownloadHandler implements HttpHandler
    {
        @Override
        public void handle(HttpExchange exchange)throws IOException
        {
            Headers headers=exchange.getRequestHeaders();
            headers.add("Access-Control-Allow-Origin","*");

            if(exchange.getRequestMethod().equalsIgnoreCase("GET"))
            {
                String response="Method not allowed";
                exchange.sendResponseHeaders(405,response.getBytes().length);
                try(OutputStream oos=exchange.getResponseBody())
                {
                    oos.write(response.getBytes());
                }
                return ;
            }

            String path=exchange.getRequestURI().getPath();
            String portStr=path.substring(path.lastIndexOf('/')+1);
            try
            {
                int port=Integer.parseInt(portStr);
                try(Socket socket=new Socket("localhost",port))
                {
                    InputStream socketInput=socket.getInputStream();
                    File tempFile=File.createTempFile("download-",".tmp");
                    String fileName="downloaded-file";
                    try(FileOutputStream fos=new FileOutputStream((tempFile)))
                    {
                        byte[] buffer=new byte[4096];
                        int byteRead;
                        ByteArrayOutputStream headerBaos=new ByteArrayOutputStream();
                        int b;
                        while((b=socketInput.read())!=-1)
                        {
                            if(b=='\n')
                                break;
                            headerBaos.write(b);
                        }
                        String header=headerBaos.toString().trim();
                        if(header.startsWith("Filename: "))
                            fileName=header.substring("Filename: ".length());

                        //int off=0;
                        while((byteRead=socketInput.read(buffer))!=-1)
                            fos.write(buffer,0,byteRead);
                    }
                    headers.add("Content-Dispositions: ","attachment; filename=\""+fileName+"\"");
                    headers.add("COntent-Type","application/octet-stream");
                    exchange.sendResponseHeaders(200,tempFile.length());
                    try(OutputStream oos=exchange.getResponseBody())
                    {
                        FileInputStream fis=new FileInputStream(tempFile);
                        byte[] buffer=new byte[4096];
                        int bytesRead;
                        while((bytesRead=fis.read(buffer))!=-1)
                            oos.write(buffer,0,bytesRead);
                    }
                    tempFile.delete();
                }

                catch(Exception e)
                {
                    System.err.println("Not able to download file:- "+e.getMessage());
                    String response="Error downloading file."+e.getMessage();
                    headers.add("Content-Type","text/plain");
                    exchange.sendResponseHeaders(400,response.getBytes().length);
                    try(OutputStream oos=exchange.getResponseBody())
                    {
                        oos.write(response.getBytes());
                    }
                }

            }
            catch (Exception e)
            {
                System.err.println("Error getting port: "+e.getMessage());
            }

        }
    }

}


