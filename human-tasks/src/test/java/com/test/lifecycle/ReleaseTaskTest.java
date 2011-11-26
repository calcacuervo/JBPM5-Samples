package com.test.lifecycle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import org.drools.KnowledgeBase;
import org.drools.logger.KnowledgeRuntimeLoggerFactory;
import org.drools.persistence.jpa.JPAKnowledgeService;
import org.drools.runtime.StatefulKnowledgeSession;
import org.drools.runtime.process.ProcessInstance;
import org.jbpm.process.audit.JPAProcessInstanceDbLog;
import org.jbpm.process.audit.JPAWorkingMemoryDbLogger;
import org.jbpm.process.audit.ProcessInstanceLog;
import org.jbpm.process.workitem.wsht.CommandBasedWSHumanTaskHandler;
import org.jbpm.task.Status;
import org.jbpm.task.query.TaskSummary;
import org.jbpm.task.service.hornetq.CommandBasedHornetQWSHumanTaskHandler;
import org.junit.Ignore;
import org.junit.Test;

import com.test.BaseHumanTaskTest;

/**
 * This test will show how to use releasing of tasks........................ The
 * current actual owner of a human task may release a task to again make it
 * available for all potential owners. A task can be released from active states
 * that have an actual owner (Reserved, InProgress), transitioning it into the
 * Ready state. Business data associated with the task (intermediate result
 * data, ad-hoc attachments and comments) is kept. A task that is currently
 * InProgress can be stopped by the actual owner, transitioning it into state
 * Reserved. Business data associated with the task as well as its actual owner
 * is kept.
 * 
 * 
 * @author calcacuervo
 * 
 */
public class ReleaseTaskTest extends BaseHumanTaskTest {

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
		return new String[] { "two-tasks-human-task-test.bpmn",
				"dynamic-user-human-task-test.bpmn" };
	}

	@Override
	protected Map<String, List<String>> getTestUserGroupsAssignments() {
		Map<String, List<String>> assign = new HashMap<String, List<String>>();
		List<String> user1Groups = new ArrayList<String>();
		List<String> user2Groups = new ArrayList<String>();
		user1Groups.add("testGroup1");

		// user2 is in both groups!
		user2Groups.add("testGroup1");
		user2Groups.add("testGroup2");
		assign.put("testUser1", user1Groups);
		assign.put("testUser2", user2Groups);
		return assign;
	}

	private StatefulKnowledgeSession session;

	/**
	 * Releases a human task.
	 * 
	 * @throws InterruptedException
	 */
	@Test
	public void releaseHumanTask() throws InterruptedException {
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

		CommandBasedHornetQWSHumanTaskHandler wsHumanTaskHandler = new CommandBasedHornetQWSHumanTaskHandler(
				session);
		wsHumanTaskHandler.setClient(client.getTaskClient());
		session.getWorkItemManager().registerWorkItemHandler("Human Task",
				wsHumanTaskHandler);
		ProcessInstance process = session.createProcessInstance("TwoTasksTest",
				null);
		session.insert(process);
		long processInstanceId = process.getId();
		session.startProcessInstance(processInstanceId);
		Thread.sleep(2000);

		// As it is potentially onwner by the testGroup1, it should be avaible
		// for testUser1 and testUser2.. let check it!
		List<TaskSummary> tasks = client.getTasksAssignedAsPotentialOwner(
				"testUser2", "en-UK",
				this.getTestUserGroupsAssignments().get("testUser2"));
		Assert.assertEquals(1, tasks.size());
		tasks = client.getTasksAssignedAsPotentialOwner("testUser1", "en-UK",
				this.getTestUserGroupsAssignments().get("testUser1"));

		Assert.assertEquals(1, tasks.size());
		long taskId = tasks.get(0).getId();

		client.claim(taskId, "testUser1", this.getTestUserGroupsAssignments()
				.get("testUser1"));

//		Thread.sleep(1000);
//		
//		//After testUser1 claims it, it should not available from testUser2 
//		tasks = client.getTasksAssignedAsPotentialOwner("testUser2", "en-UK",
//				this.getTestUserGroupsAssignments().get("testUser2"));
//
//		Assert.assertEquals(0, tasks.size());

		client.start(taskId, "testUser1");

		Thread.sleep(1000);
		
		// Now, release the task
		client.release(taskId, "testUser1");

		// Now it should be available for all people in testGroup1. It includes testUser2!
		tasks = client.getTasksAssignedAsPotentialOwner("testUser2", "en-UK",
				this.getTestUserGroupsAssignments().get("testUser2"));

		Assert.assertEquals(1, tasks.size());
		taskId = tasks.get(0).getId();
		client.claim(taskId, "testUser1", this.getTestUserGroupsAssignments().get("testUser2"));
		client.start(taskId, "testUser1");
		client.complete(taskId, "testUser1", null);

		tasks = client.getTasksOwned("testUser1", "en-UK");
		Assert.assertEquals(1, tasks.size());
		Assert.assertEquals(Status.Completed, tasks.get(0).getStatus());

		// And then the process continues.
		List<TaskSummary> tasksUser2 = client.getTasksAssignedAsPotentialOwner(
				"testUser2", "en-UK",
				this.getTestUserGroupsAssignments().get("testUser2"));

		Assert.assertEquals(1, tasksUser2.size());
		taskId = tasksUser2.get(0).getId();
		client.claim(taskId, "testUser2", this.getTestUserGroupsAssignments()
				.get("testUser2"));
		client.start(taskId, "testUser2");
		client.complete(taskId, "testUser2", null);

		// Reload the tasks to see new status.
		tasksUser2 = client.getTasksOwned("testUser2", "en-UK");
		Assert.assertEquals(1, tasksUser2.size());
		Assert.assertEquals(Status.Completed, tasksUser2.get(0).getStatus());

		// now check in the logs the process finished.
		ProcessInstanceLog processInstanceLog = processLog
				.findProcessInstance(processInstanceId);
		Assert.assertNotNull(processInstanceLog.getEnd());
	}
}
