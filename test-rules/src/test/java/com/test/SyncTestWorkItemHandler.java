package com.test;

import java.util.ArrayList;
import java.util.List;
import org.drools.runtime.process.WorkItem;
import org.drools.runtime.process.WorkItemHandler;
import org.drools.runtime.process.WorkItemManager;

public class SyncTestWorkItemHandler implements WorkItemHandler {

    private List<String> executions = new ArrayList<String>();
    private List<String> abortions = new ArrayList<String>();

    public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {
        executions.add(workItem.getId() + " - " + workItem.getName());
        System.out.println("Work Item Execution = "+workItem.getId() + " - " + workItem.getName());
        manager.completeWorkItem(workItem.getId(), null);

    }

    public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {
        abortions.add(workItem.getId() + " - " + workItem.getName());
    }

    public List<String> getAbortions() {
        return abortions;
    }

    public List<String> getExecutions() {
        return executions;
    }
}