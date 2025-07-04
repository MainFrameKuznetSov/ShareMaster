package p2p.service;
import main.java.p2p.utility.UploadUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

public class FileSharer
{
    private HashMap<Integer,String> presentFiles;
    public FileSharer()
    {
        this.presentFiles=new HashMap<>();
    }

    public int offerFile(String path)
    {
        int port;
        while(true)
        {
            port=UploadUtil.generatePort();
            if(!presentFiles.containsKey(port))
            {
                presentFiles.put(port, path);
                return port;
            }
        }
    }

    public void startFileServer(int port)
    {
        String path=presentFiles.get(port);
        if(path==null)
        {
            System.out.println("No file found in the port "+port);
            return ;
        }

        try(ServerSocket serverSocket=new ServerSocket(port))
        {
            System.out.println("Processing File:- "+new File(path)+" on port:- "+port);
            Socket clientSocket=serverSocket.accept();
            System.out.println("Client address established at:- "+clientSocket.getInetAddress());
            new Thread(new FileSenderHandler(clientSocket,path)).start();

        }
        catch(IOException e)
        {
            System.out.println("Error handling file on port "+port);
        }
    }

    private static class FileSenderHandler implements Runnable
    {

        private final Socket clientSocket;
        private final String path;

        public FileSenderHandler(Socket clientSocket,String path)
        {
            this.clientSocket=clientSocket;
            this.path=path;
        }

        @Override
        public void run()
        {
            try(FileInputStream fis=new FileInputStream(path))
            {
                OutputStream oos=clientSocket.getOutputStream();
                String fileName=new File(path).getName();
                String header="Filename:- "+fileName+"\n";
                oos.write(header.getBytes());

                byte buffer[]=new byte[4096];
                int byteRead;
                while((byteRead=fis.read(buffer))!=-1)
                    oos.write(buffer,0,byteRead);
                System.out.println("File "+fileName+" sent to "+clientSocket.getInetAddress());
            }
            catch(Exception e)
            {
                System.out.println("Error occurred while sending to client:- "+e.getMessage());
            }
            finally
            {
                try
                {
                    clientSocket.close();
                }
                catch(Exception e)
                {
                    System.out.println("Error closing socket:- "+e.getMessage());
                }
            }
        }
    }

}
