import com.virjar.echo.nat.client.EchoClient;

/**
 * 请注意，client只能在这里运行测试，不能在echo-lib这个包下运行。哪里依赖环境不完整
 */
public class ClientTest {
    public static void main(String[] args) {
        EchoClient echoClient = new EchoClient("127.0.0.1", 5698, "clientId-virjar-test");
        echoClient.setAdminAccount("virjar");
        echoClient.startUp();

        // EchoLogger.setLogger(new SystemOutLogger());
        while (true) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
