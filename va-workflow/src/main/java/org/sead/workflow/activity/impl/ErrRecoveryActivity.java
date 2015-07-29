package org.sead.workflow.activity.impl;

import org.sead.workflow.activity.AbstractWorkflowActivity;
import org.sead.workflow.config.SeadWorkflowConfig;
import org.sead.workflow.context.SeadWorkflowContext;

/**
 * Responsible for post-error logging and processing for unsuccessful workflow
*/

public class ErrRecoveryActivity extends AbstractWorkflowActivity {

    @Override
    public void execute(SeadWorkflowContext context, SeadWorkflowConfig config) {
        System.out.println("Executing activity : " + activityName);
    }

}