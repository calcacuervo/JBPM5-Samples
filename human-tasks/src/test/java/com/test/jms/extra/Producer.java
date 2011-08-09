package com.test.jms.extra;

import javax.jms.ConnectionFactory;
import javax.jms.TextMessage;
import javax.sql.DataSource;
import javax.transaction.TransactionManager;

public class Producer {

    private TransactionManager tm;
    private ConnectionFactory connectionFactory;
 
    private String queueName;

    public Producer(TransactionManager tm, ConnectionFactory connectionFactory, String queueName) {
        this.tm = tm;
        this.connectionFactory = connectionFactory;
        this.queueName = queueName;
    }

    public void run() {
        while (true) {
            try {
                tm.begin();

                System.out.println("producing message on " + queueName + "...");
                JMSHelper.sendSampleMessage(connectionFactory, queueName, 20000);
                
                tm.commit();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}
