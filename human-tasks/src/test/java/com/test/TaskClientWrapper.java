package com.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import org.jbpm.process.workitem.wsht.BlockingGetTaskResponseHandler;
import org.jbpm.task.AccessType;
import org.jbpm.task.Content;
import org.jbpm.task.Task;
import org.jbpm.task.query.TaskSummary;
import org.jbpm.task.service.ContentData;
import org.jbpm.task.service.TaskClient;
import org.jbpm.task.service.TaskClientHandler.QueryGenericResponseHandler;
import org.jbpm.task.service.responsehandlers.BlockingGetContentResponseHandler;
import org.jbpm.task.service.responsehandlers.BlockingQueryGenericResponseHandler;
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

	/**
	 * Creates a new {@link TaskClientWrapper} instance.
	 * 
	 * @param taskClient
	 */
	public TaskClientWrapper(final TaskClient taskClient) {
		this.client = taskClient;
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
		client.claim(taskId, userId, groups, claimOperationResponseHandler);
		claimOperationResponseHandler.waitTillDone(1000);
	}

	public void delegate(long taskId, final String userId, String targetUserId) {
		BlockingTaskOperationResponseHandler responseHandler = new BlockingTaskOperationResponseHandler();
		client.delegate(taskId, userId, targetUserId, responseHandler);
		responseHandler.waitTillDone(1000);
		// Wait for some time so that the process follows.
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public void remove(long taskId, final String userId) {
		BlockingTaskOperationResponseHandler responseHandler = new BlockingTaskOperationResponseHandler();
		client.remove(taskId, userId, responseHandler);
		responseHandler.waitTillDone(1000);
	}

	public void release(long taskId, final String userId) {
		BlockingTaskOperationResponseHandler responseHandler = new BlockingTaskOperationResponseHandler();
		client.release(taskId, userId, responseHandler);
		responseHandler.waitTillDone(1000);
	}

	public void resume(long taskId, final String userId) {
		BlockingTaskOperationResponseHandler responseHandler = new BlockingTaskOperationResponseHandler();
		client.resume(taskId, userId, responseHandler);
		responseHandler.waitTillDone(1000);
	}

	/**
	 * User starts a task.
	 * 
	 * @param taskId
	 * @param userId
	 */
	public void start(long taskId, final String userId) {
		BlockingTaskOperationResponseHandler startOperationResponseHandler = new BlockingTaskOperationResponseHandler();
		client.start(taskId, userId, startOperationResponseHandler);
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
		client.complete(taskId, userId, data, completeOperationResponseHandler);
		completeOperationResponseHandler.waitTillDone(1000);

		// Wait for some time so that the process follows.
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public void skip(long taskId, final String userId) {
		BlockingTaskOperationResponseHandler completeOperationResponseHandler = new BlockingTaskOperationResponseHandler();
		client.skip(taskId, userId, completeOperationResponseHandler);
		completeOperationResponseHandler.waitTillDone(1000);
		// Wait for some time so that the process follows.
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public List<TaskSummary> getTasksAssignedAsPotentialOwner(
			final String userId, String language, List<String> groups) {
		if (language == null) {
			language = "en-UK";
		}
		BlockingTaskSummaryResponseHandler responseHandler = null;
		if (groups == null || groups.isEmpty()) {
			responseHandler = new BlockingTaskSummaryResponseHandler();
			client.getTasksAssignedAsPotentialOwner(userId, language,
					responseHandler);
			responseHandler.waitTillDone(1000);
			List<TaskSummary> tasks = responseHandler.getResults();
			return tasks;
		} else {
			responseHandler = new BlockingTaskSummaryResponseHandler();
			client.getTasksAssignedAsPotentialOwner(userId, groups, language,
					responseHandler);
			responseHandler.waitTillDone(1000);
			List<TaskSummary> tasks = responseHandler.getResults();
			return tasks;
		}
	}

	public List<TaskSummary> getTasksOwned(final String userId, String language) {
		if (language == null) {
			language = "en-UK";
		}
		BlockingTaskSummaryResponseHandler responseHandler = new BlockingTaskSummaryResponseHandler();
		client.getTasksOwned(userId, language, responseHandler);
		responseHandler.waitTillDone(1000);
		List<TaskSummary> tasks = responseHandler.getResults();
		return tasks;
	}

	public Task getTask(long taskId) {
		BlockingGetTaskResponseHandler responseHandler = new BlockingGetTaskResponseHandler();
		client.getTask(taskId, responseHandler);
		responseHandler.waitTillDone(1000);
		return responseHandler.getTask();
	}

	public Object getTaskContent(long taskId) {
		Task task = this.getTask(taskId);
		BlockingGetContentResponseHandler contentResponseHandler = new BlockingGetContentResponseHandler();
		client.getContent(task.getTaskData().getDocumentContentId(),
				contentResponseHandler);

		Content content = contentResponseHandler.getContent();

		ByteArrayInputStream bais = new ByteArrayInputStream(
				content.getContent());

		ObjectInputStream ois;
		try {
			ois = new ObjectInputStream(bais);
			Object vars = ois.readObject();
			return vars;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}
	
	public List query(String hqlQuery, int size) {
		BlockingQueryGenericResponseHandler responseHandler = new BlockingQueryGenericResponseHandler();
		client.query(hqlQuery, size, 0, responseHandler);
		responseHandler.waitTillDone(1000);
		return responseHandler.getResults();
	}
}
