import LightingSystem.Light;
import LightingSystem.SocketClient;

import java.net.InetSocketAddress;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args){
        // 本番用
        //IlsServer server = new IlsServer("localhost", 44344);

        // テスト用
//        IlsServer server = new IlsServer("192.168.1.54", 12345);
        IlsServer server = new IlsServer("192.168.1.30", 12345);

        server.run();
    }
}
