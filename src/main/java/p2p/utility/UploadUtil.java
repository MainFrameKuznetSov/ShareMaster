package p2p.utility;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class UploadUtil
{
    public static int generatePort()throws IOException
    {
        final int START_PORT=49152;
        final int END_PORT=65535;
        final int MAX_TRIES=20_000;
        final int range=END_PORT-START_PORT+1;
        for(int trial=0;trial<Math.min(MAX_TRIES,range);++trial)
        {
            int port=ThreadLocalRandom.current().nextInt(range) + START_PORT;
            try(ServerSocket ss=new ServerSocket(port))
            {
                return port;
            }
            catch (IOException e)
            {}
        }
        throw new IOException("Port not found even after "+MAX_TRIES+" trials.");
    }
}
