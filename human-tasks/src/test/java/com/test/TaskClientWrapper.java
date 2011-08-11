package com.test;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.transaction.NotSupportedException;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;

import org.jbpm.task.AccessType;
import org.jbpm.task.query.TaskSummary;
import org.jbpm.task.service.ContentData;
import org.jbpm.task.service.TaskClient;
import org.jbpm.task.service.responsehandlers.BlockingTaskOperationResponseHandler;
import org.jbpm.task.service.responsehandlers.BlockingTaskSummaryResponseHandler;

/**
 * Client that wraps {@link TaskClient}, to avoid using the response handlers in
 * all places.
 * 
 * @author calcacuervo
 * 
 */
public class TaskClientWrapper {

	/**
	 * The wrapped client.
	 */
	private TaskClient client;

	private boolean surroundWithTransacion = false;
	
	private TransactionManager transactionManager;
	/**
	 * Creates a new {@link TaskClientWrapper} instance.
	 * 
	 * @param taskClient
	 */
	public TaskClientWrapper(final TaskClient taskClient, TransactionManager transactionManager) {
		this.client = taskClient;
		if (transactionManager != null) {
			this.surroundWithTransacion = true;
			this.transactionManager = transactionManager;
		}
	}

	public void connect(String ipAddress, int port) {
		this.client.connect(ipAddress, port);
	}

	public void disconnect() throws Exception {
		this.client.disconnect();
	}

	public TaskClient getTaskClient() {
		return this.client;
	}

	/**
	 * Claims a task which is assigned to a group that the given user is
	 * assigned to.
	 * 
	 * @param taskId
	 * @param userId
	 */
	public void claim(long taskId, final String userId, List<String> groups) {
		BlockingTaskOperationResponseHandler claimOperationResponseHandler = new BlockingTaskOperationResponseHandler();
		if (this.surroundWithTransacion) {
			try {
				this.transactionManager.begin();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		client.claim(taskId, userId, groups, claimOperationResponseHandler);
		if (this.surroundWithTransacion) {
			try {
				this.transactionManager.commit();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		claimOperationResponseHandler.waitTillDone(1000);
	}

	/**
	 * User starts a task.
	 * 
	 * @param taskId
	 * @param userId
	 */
	public void start(long taskId, final String userId) {
		BlockingTaskOperationResponseHandler startOperationResponseHandler = new BlockingTaskOperationResponseHandler();
		if (this.surroundWithTransacion) {
			try {
				this.transactionManager.begin();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		client.start(taskId, userId, startOperationResponseHandler);
		if (this.surroundWithTransacion) {
			try {
				this.transactionManager.commit();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		startOperationResponseHandler.waitTillDone(1000);
	}

	/**
	 * User completes a task.
	 * 
	 * @param taskId
	 * @param userId
	 * @param result
	 */
	public void complete(long taskId, final String userId, final Object result) {
		ContentData data = new ContentData();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ObjectOutputStream oos = null;
		try {
			oos = new ObjectOutputStream(baos);
			oos.writeObject(result);
			oos.close();
			data.setContent(baos.toByteArray());
			data.setAccessType(AccessType.Inline);
		} catch (Exception e) {

		}
		BlockingTaskOperationResponseHandler completeOperationResponseHandler = new BlockingTaskOperationResponseHandler();
		if (this.surroundWithTransacion) {
			try {
				this.transactionManager.begin();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		client.complete(taskId, userId, null, completeOperationResponseHandler);
		if (this.surroundWithTransacion) {
			try {
				this.transactionManager.commit();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		completeOperationResponseHandler.waitTillDone(1000);
	}

	public List<TaskSummary> getTasksAssignedAsPotentialOwner(
			final String userId, String language, List<String> groups) {
		if (language == null) {
			language = "en-UK";
		}
		BlockingTaskSummaryResponseHandler responseHandler = null;
		if (groups.isEmpty()) {
			responseHandler = new BlockingTaskSummaryResponseHandler();
			if (this.surroundWithTransacion) {
				try {
					this.transactionManager.begin();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			client.getTasksAssignedAsPotentialOwner(userId, language,
					responseHandler);
			if (this.surroundWithTransacion) {
				try {
					this.transactionManager.commit();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} else {
			responseHandler = new BlockingTaskSummaryResponseHandler();
			if (this.surroundWithTransacion) {
				try {
					this.transactionManager.begin();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			client.getTasksAssignedAsPotentialOwner(userId, groups, language,
					responseHandler);
			if (this.surroundWithTransacion) {
				try {
					System.out.println("TESTTTTTT");
					this.transactionManager.commit();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			responseHandler.waitTillDone(1000);
		}

		List<TaskSummary> tasks = responseHandler.getResults();
		return tasks;
	}

	public List<TaskSummary> getTasksOwned(final String userId, String language) {
		if (language == null) {
			language = "en-UK";
		}
		BlockingTaskSummaryResponseHandler responseHandler = new BlockingTaskSummaryResponseHandler();
		if (this.surroundWithTransacion) {
			try {
				this.transactionManager.begin();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		client.getTasksOwned(userId, language, responseHandler);
		if (this.surroundWithTransacion) {
			try {
				this.transactionManager.commit();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		responseHandler.waitTillDone(1000);
		List<TaskSummary> tasks = responseHandler.getResults();
		return tasks;
	}
}
