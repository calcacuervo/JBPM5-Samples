package com.test.jms.another;

import javax.jms.ConnectionFactory;
import javax.jms.TextMessage;
import javax.sql.DataSource;
import javax.transaction.TransactionManager;

public class Consumer implements Runnable {

    private TransactionManager tm;
    private ConnectionFactory connectionFactory;
    private String queueName;

    public Consumer(TransactionManager tm, ConnectionFactory connectionFactory, String queueName) {
        this.tm = tm;
        this.connectionFactory = connectionFactory;
        this.queueName = queueName;
    }

    public void run() {
        while (true) {
            try {
//                tm.begin();

                System.out.println("receiving message on " + queueName + "...");
                TextMessage message = (TextMessage) JMSHelper.readMessage(connectionFactory, queueName, 3000L);
                if (message != null) {
                    System.out.println("received message");
                }

//                tm.commit();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}
