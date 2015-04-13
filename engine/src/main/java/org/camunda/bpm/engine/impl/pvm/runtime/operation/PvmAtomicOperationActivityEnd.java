/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.camunda.bpm.engine.impl.pvm.runtime.operation;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.pvm.delegate.ActivityBehavior;
import org.camunda.bpm.engine.impl.pvm.delegate.CompositeActivityBehavior;
import org.camunda.bpm.engine.impl.pvm.process.ActivityImpl;
import org.camunda.bpm.engine.impl.pvm.process.ScopeImpl;
import org.camunda.bpm.engine.impl.pvm.runtime.PvmExecutionImpl;

/**
 * @author Tom Baeyens
 * @author Daniel Meyer
 * @author Thorben Lindhauer
 */
public class PvmAtomicOperationActivityEnd implements PvmAtomicOperation {

  protected ScopeImpl getScope(PvmExecutionImpl execution) {
    return execution.getActivity();
  }

  public boolean isAsync(PvmExecutionImpl execution) {
    return execution.getActivity().isAsyncAfter();
  }

  public void execute(PvmExecutionImpl execution) {
    // restore activity instance id
    if (execution.getActivityInstanceId() == null) {
      if (execution.isProcessInstanceExecution()) {
        execution.setActivityInstanceId(execution.getId());
      }
      else {
        execution.setActivityInstanceId(execution.getParentActivityInstanceId());
      }
    }

    ActivityImpl activity = execution.getActivity();

    PvmExecutionImpl propagatingExecution = execution;

    if(execution.isScope() && activity.isScope()) {
      execution.destroy();
      if(!execution.isConcurrent()) {
        execution.remove();
        propagatingExecution = execution.getParent();
        propagatingExecution.setActivity(execution.getActivity());
      }
    }

    ScopeImpl flowScope = activity.getFlowScope();

    // 1. flow scope = Process Definition
    if(flowScope == activity.getProcessDefinition()) {

      // 1.1 concurrent execution => end + tryPrune()
      if(propagatingExecution.isConcurrent()) {
        propagatingExecution.remove();
        propagatingExecution.getParent().tryPruneLastConcurrentChild();
      }
      else {
        // 1.2 Process End
        propagatingExecution.performOperation(PROCESS_END);
      }
    }
    else {
      // 2. flowScope != process definition
      ActivityImpl flowScopeActivity = (ActivityImpl) flowScope;
      propagatingExecution.setActivity(flowScopeActivity);

      ActivityBehavior activityBehavior = flowScopeActivity.getActivityBehavior();
      if (activityBehavior instanceof CompositeActivityBehavior) {
        CompositeActivityBehavior compositeActivityBehavior = (CompositeActivityBehavior) activityBehavior;
        // 2.1 Concurrent execution => composite behavior.concurrentExecutionEnded()
        if(propagatingExecution.isConcurrent()) {
          compositeActivityBehavior.concurrentChildExecutionEnded(propagatingExecution.getParent(), propagatingExecution);
        }
        else {
          // 2.2 Scope Execution => composite behavior.complete()
          compositeActivityBehavior.complete(propagatingExecution);
        }

      }
      else {
        // activity behavior is not composite => this is unexpected
        throw new ProcessEngineException("Expected behavior of composite scope "+activity
            +" to be a CompositeActivityBehavior but got "+activityBehavior);
      }
    }
  }

  public String getCanonicalName() {
    return "activity-end";
  }

}
