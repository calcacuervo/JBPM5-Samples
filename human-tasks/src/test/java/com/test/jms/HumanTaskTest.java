package com.test.jms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.InitialContext;
import javax.persistence.EntityNotFoundException;
import javax.transaction.UserTransaction;

import junit.framework.Assert;

import org.drools.KnowledgeBase;
import org.drools.logger.KnowledgeRuntimeLoggerFactory;
import org.drools.persistence.jpa.JPAKnowledgeService;
import org.drools.runtime.StatefulKnowledgeSession;
import org.drools.runtime.process.ProcessInstance;
import org.jbpm.process.audit.JPAProcessInstanceDbLog;
import org.jbpm.process.audit.JPAWorkingMemoryDbLogger;
import org.jbpm.process.workitem.wsht.CommandBasedWSHumanTaskHandler;
import org.jbpm.task.I18NText;
import org.jbpm.task.Status;
import org.jbpm.task.Task;
import org.jbpm.task.TaskData;
import org.jbpm.task.User;
import org.jbpm.task.query.TaskSummary;
import org.jbpm.task.service.ContentData;
import org.jbpm.task.service.TaskClient;
import org.jbpm.task.service.responsehandlers.BlockingAddTaskResponseHandler;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HumanTaskTest extends BaseHumanTaskTest {

	private static final Logger logger = LoggerFactory.getLogger(HumanTaskTest.class);
	@Override
	protected String[] getTestUsers() {
		return new String[] { "testUser1", "testUser2", "testUser3",
				"Administrator" };
	}

	@Override
	protected String[] getTestGroups() {
		return new String[] { "testGroup1", "testGroup2" };
	}

	@Override
	protected String[] getProcessPaths() {
		return new String[] { /**"two-tasks-human-task-test.bpmn", "two-tasks-human-task-assigned-to-actortest.bpmn"**/ };
	}

	@Override
	protected Map<String, Set<String>> getTestUserGroupsAssignments() {
		Map<String, Set<String>> assign = new HashMap<String, Set<String>>();
		Set<String> user1Groups = new HashSet<String>();
		user1Groups.add("testGroup1");
		user1Groups.add("testGroup2");
		assign.put("testUser1", user1Groups);
		assign.put("testUser2", user1Groups);
		return assign;
	}

	private StatefulKnowledgeSession session;

	@Test
	public void twoHumanTasksCompleted() throws Exception {
		KnowledgeBase kbase = this.createKnowledgeBase();
		session = JPAKnowledgeService.newStatefulKnowledgeSession(kbase, null,
				env);

		// this will log in audit tables
		new JPAWorkingMemoryDbLogger(session);

		KnowledgeRuntimeLoggerFactory.newConsoleLogger(session);

		// Logger that will give information about the process state, variables,
		// etc
		JPAProcessInstanceDbLog processLog = new JPAProcessInstanceDbLog(
				session.getEnvironment());

		TaskClient tc = this.client;
		Task task = new Task();
		List<I18NText> names1 = new ArrayList<I18NText>();
		I18NText text1 = new I18NText("en-UK", "tarea1");
		names1.add(text1);
		task.setNames(names1);
		TaskData taskData = new TaskData();
		taskData.setStatus(Status.Created);
		taskData.setCreatedBy(new User("usr0"));
		taskData.setActualOwner(new User("usr0"));
		task.setTaskData(taskData);
		
		ContentData data = new ContentData();
		BlockingAddTaskResponseHandler addTaskHandler = new BlockingAddTaskResponseHandler();
		tc.addTask(new Task(), data, addTaskHandler);
		long taskId = addTaskHandler.getTaskId();

		tc.disconnect();
		
		tc.connect();
	}		
}
