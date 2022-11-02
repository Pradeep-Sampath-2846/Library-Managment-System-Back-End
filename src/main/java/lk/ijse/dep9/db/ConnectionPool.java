package lk.ijse.dep9.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ConnectionPool {

    private List<Connection> pool =new ArrayList<>();
    private List<Connection> consumerPool =new ArrayList<>();
    private final int poolSize;

    public ConnectionPool(int poolSize){
        this.poolSize= poolSize;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            for (int i = 0; i < poolSize; i++) {
                Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/dep9_lms", "root", "KPsuneetha@123");
                pool.add(connection);

            }

        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    public synchronized Connection getConnection(){
        while (pool.isEmpty()){
            try {
                wait();  //can be spurious wakeup happen here
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        Connection connection = pool.get(0);
        consumerPool.add(connection);
        pool.remove(connection);
        return connection;
    }

    public synchronized void releaseConnection(Connection connection){
        pool.add(connection);
        consumerPool.remove(connection);

        notify();

    }

    public synchronized void releaseAllConnection(){
        pool.addAll(consumerPool);
        consumerPool.clear();

        notifyAll();

    }
}
