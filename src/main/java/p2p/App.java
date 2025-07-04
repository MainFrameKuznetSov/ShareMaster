package p2p;

import p2p.controller.FileController;

public class App
{
    public static void main(String[] args)
    {
        try
        {
            FileController fileController = new FileController(8080);
            fileController.start();
            System.out.println("ShareAll server started at 8080");
            Runtime.getRuntime().addShutdownHook(
                    new Thread(
                            ()->{
                                System.out.println("Shutting server.");
                                fileController.stop();
                            }
                    )
            );

            System.out.println("Press Enter to stop server");
        }
        catch(Exception e)
        {
            System.err.println("Failed to start server at port 8080");
            e.printStackTrace();
        }
    }
}
