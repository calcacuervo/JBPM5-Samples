package com.test.group;

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
import org.jbpm.process.audit.JPAWorkingMemoryDbLogger;
import org.jbpm.process.workitem.wsht.CommandBasedWSHumanTaskHandler;
import org.jbpm.task.Status;
import org.jbpm.task.query.TaskSummary;
import org.jbpm.task.service.PermissionDeniedException;
import org.jbpm.task.service.hornetq.CommandBasedHornetQWSHumanTaskHandler;
import org.junit.Test;

import com.test.BaseHumanTaskTest;

/**
 * Tests to show how to use tasks assigning them to groups. Jbpm5 does not hold
 * the user/group relations, so we have to manually pass the user and the groups
 * he is assigned to. This information would be managed by the application which
 * uses jbpm5.
 * 
 * @author calcacuervo
 * 
 */
public class HumanTaskGroupTest extends BaseHumanTaskTest {

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
		return new String[] { "simple-human-task-test.bpmn" };
	}

	@Override
	protected Map<String, List<String>> getTestUserGroupsAssignments() {
		Map<String, List<String>> assign = new HashMap<String, List<String>>();
		List<String> user1Groups = new ArrayList<String>();
		List<String> user2Groups = new ArrayList<String>();
		user1Groups.add("testGroup1");
		user2Groups.add("testGroup2");
		assign.put("testUser1", user1Groups);
		assign.put("testUser2", user2Groups);
		return assign;
	}

	private StatefulKnowledgeSession session;

	/**
	 * This tests simply shows how to use a task which is assigned to a group.
	 * 
	 * @throws InterruptedException
	 */
	@Test
	public void taskAssignedToGroup() throws InterruptedException {
		KnowledgeBase kbase = this.createKnowledgeBase();
		session = JPAKnowledgeService.newStatefulKnowledgeSession(kbase, null,
				env);
		new JPAWorkingMemoryDbLogger(session);
		KnowledgeRuntimeLoggerFactory.newConsoleLogger(session);
		CommandBasedHornetQWSHumanTaskHandler wsHumanTaskHandler = new CommandBasedHornetQWSHumanTaskHandler(
				session);
		wsHumanTaskHandler.setClient(client.getTaskClient());
		session.getWorkItemManager().registerWorkItemHandler("Human Task",
				wsHumanTaskHandler);
		ProcessInstance process = session.createProcessInstance("SimpleTest",
				null);
		session.insert(process);
		long processInstanceId = process.getId();
		session.startProcessInstance(processInstanceId);
		Thread.sleep(2000);

		// Pass the user and the group it belongs, to get the tasks assigned to him direcly and the ones for its groups.
		List<TaskSummary> tasks = client.getTasksAssignedAsPotentialOwner(
				"testUser1", "en-UK", this.getTestUserGroupsAssignments().get("testUser1"));

		Assert.assertEquals(1, tasks.size());

		// Pass the user and the group it belongs
		client.claim(tasks.get(0).getId(), "testUser1", this.getTestUserGroupsAssignments().get("testUser1"));

		// The task owned method will give the tasks for a user which have been already claimed by him.
		tasks = client.getTasksOwned("testUser1", "en-UK");
		Assert.assertEquals(1, tasks.size());

		client.start(tasks.get(0).getId(), "testUser1");

		client.complete(tasks.get(0).getId(), "testUser1", null);

		tasks = client.getTasksOwned("testUser1", "en-UK");
		Assert.assertEquals(1, tasks.size());
		Assert.assertEquals(Status.Completed, tasks.get(0).getStatus());
		
	}

	/**
	 * This test will show what will happen if a user which is not potential
	 * owner tries to make an unauthorized claim. This will throw a {@link PermissionDeniedException}.
	 * 
	 * @throws InterruptedException
	 */
	@Test(expected = PermissionDeniedException.class)
	public void unauthorizedAttempt() throws InterruptedException {
		KnowledgeBase kbase = this.createKnowledgeBase();
		session = JPAKnowledgeService.newStatefulKnowledgeSession(kbase, null,
				env);
		new JPAWorkingMemoryDbLogger(session);
		KnowledgeRuntimeLoggerFactory.newConsoleLogger(session);
		CommandBasedHornetQWSHumanTaskHandler wsHumanTaskHandler = new CommandBasedHornetQWSHumanTaskHandler(
				session);
		wsHumanTaskHandler.setClient(client.getTaskClient());
		session.getWorkItemManager().registerWorkItemHandler("Human Task",
				wsHumanTaskHandler);
		ProcessInstance process = session.createProcessInstance("SimpleTest",
				null);
		session.insert(process);
		long processInstanceId = process.getId();
		session.startProcessInstance(processInstanceId);
		Thread.sleep(2000);
		List<TaskSummary> tasks = client.getTasksAssignedAsPotentialOwner(
				"testUser2", "en-UK", this.getTestUserGroupsAssignments().get("testUser2"));

		Assert.assertEquals(0, tasks.size());

		// OK, the user was no assignments, but will try to access to an
		// authorized id.
		tasks = client.getTasksAssignedAsPotentialOwner("testUser1", "en-UK",
				this.getTestUserGroupsAssignments().get("testUser1"));

		Assert.assertEquals(1, tasks.size());

		client.claim(tasks.get(0).getId(), "testUser2", this.getTestUserGroupsAssignments().get("testUser2"));

	}
}
