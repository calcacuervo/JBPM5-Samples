package com.test.jms;

import java.util.Properties;

import javax.naming.Context;
import javax.transaction.TransactionManager;

import org.drools.SystemEventListenerFactory;
import org.jbpm.task.service.TaskService;

public class JMSTaskServer extends BaseJMSTaskServer {

	public JMSTaskServer(TaskService service, Properties connProperties,
			Context context, TransactionManager tm) {
		super(new JMSTaskServerHandler(service,
				SystemEventListenerFactory.getSystemEventListener()),
				connProperties, context, tm);
	}
}
