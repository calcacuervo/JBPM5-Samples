package com.test.practice.lifecycle;

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
		return new String[] { "demian", "john", "demian's friend",
				"Administrator" };
	}

	@Override
	protected String[] getTestGroups() {
		return new String[] { "hr", "dev" };
	}

	@Override
	protected String[] getProcessPaths() {
		return new String[] { "com/test/practice/02-simple-group-asignment.bpmn" };
	}

	@Override
	protected Map<String, List<String>> getTestUserGroupsAssignments() {
		Map<String, List<String>> assign = new HashMap<String, List<String>>();
		List<String> user1Groups = new ArrayList<String>();
		user1Groups.add("hr");
		assign.put("demian", user1Groups);
		assign.put("john", user1Groups);
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
		ProcessInstance process = session.createProcessInstance("SimpleGroupAsignment",
				null);
		session.insert(process);
		long processInstanceId = process.getId();
		session.startProcessInstance(processInstanceId);
		Thread.sleep(2000);

		// As it is potentially onwned by the hr, it should be avaible
		// for demian and john.. let check it!
		List<TaskSummary> tasks = client.getTasksAssignedAsPotentialOwner(
				"demian", "en-UK",
				this.getTestUserGroupsAssignments().get("demian"));
		Assert.assertEquals(1, tasks.size());
		tasks = client.getTasksAssignedAsPotentialOwner("john", "en-UK",
				this.getTestUserGroupsAssignments().get("john"));

		Assert.assertEquals(1, tasks.size());
		long taskId = tasks.get(0).getId();

		client.claim(taskId, "demian", this.getTestUserGroupsAssignments()
				.get("demian"));

//		Thread.sleep(1000);
//		
//		//After testUser1 claims it, it should not available from testUser2 
//		tasks = client.getTasksAssignedAsPotentialOwner("testUser2", "en-UK",
//				this.getTestUserGroupsAssignments().get("testUser2"));
//
//		Assert.assertEquals(0, tasks.size());

		client.start(taskId, "demian");

		Thread.sleep(1000);
		
		// Now, release the task
		client.release(taskId, "demian");

		// Now it should be available for all people in hr. It includes john!
		tasks = client.getTasksAssignedAsPotentialOwner("john", "en-UK",
				this.getTestUserGroupsAssignments().get("john"));

		Assert.assertEquals(1, tasks.size());
		taskId = tasks.get(0).getId();
		client.claim(taskId, "john", this.getTestUserGroupsAssignments().get("john"));
		client.start(taskId, "john");
		client.complete(taskId, "john", null);

		tasks = client.getTasksOwned("john", "en-UK");
		Assert.assertEquals(1, tasks.size());
		Assert.assertEquals(Status.Completed, tasks.get(0).getStatus());

		// And then the process continues.
		// now check in the logs the process finished.
		ProcessInstanceLog processInstanceLog = processLog
				.findProcessInstance(processInstanceId);
		Assert.assertNotNull(processInstanceLog.getEnd());
	}
}
