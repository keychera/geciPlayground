package self.chera.grpc;

import com.google.protobuf.GeneratedMessageV3;

import java.time.LocalDateTime;

public class RPCDebuggingListener implements RPCActionListener {
    @Override
    public void responseListener(String actionName, GeneratedMessageV3 request, GeneratedMessageV3 response) {
        print(request);
        print(response);
    }

    @Override
    public void failureListener(String actionName, GeneratedMessageV3 request, RPCException exception) {
        print(request);
        System.out.println(exception.getMessage());
    }

    @Override
    public void addHeaderListener(CharSequence name, Object value) {
        System.out.printf("added header { %s: %s }%n", name, value);
    }

    @Override
    public void setHeaderListener(CharSequence name, Object value) {
        System.out.printf("set header { %s: %s }%n", name, value);
    }

    private void print(GeneratedMessageV3 msg) {
        var execTime = LocalDateTime.now().toString();
        System.out.println("Date Time: " + execTime);
        System.out.println(msg.getClass().getSimpleName() + ": \n" + msg);
    }
}
