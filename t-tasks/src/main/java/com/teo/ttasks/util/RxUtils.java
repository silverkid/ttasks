package com.teo.ttasks.util;

import com.mikepenz.fastadapter.IItem;
import com.teo.ttasks.R;
import com.teo.ttasks.data.model.TTask;
import com.teo.ttasks.data.model.Task;
import com.teo.ttasks.data.model.TaskList;
import com.teo.ttasks.ui.items.CategoryItem;
import com.teo.ttasks.ui.items.TaskItem;
import com.teo.ttasks.ui.items.TaskListItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.realm.RealmResults;
import rx.Observable.Transformer;

public class RxUtils {

    private RxUtils() { }

    /**
     * 1. Creates {@link TaskItem}s from {@link Task}s<br>
     * 2. Groups them by completion status (not completed or no due date first followed by completed ones)<br>
     * 3. Sorts the first group by due date and the second group by completion date
     */
    public static Transformer<RealmResults<TTask>, List<IItem>> getTaskItems(boolean hideCompleted) {
        return observable -> observable
                .map(tasks -> {
                    List<IItem> taskItems = new ArrayList<>();
                    List<TaskItem> activeTasks = new ArrayList<>();
                    List<TaskItem> completedTasks = new ArrayList<>();

                    for (TTask task : tasks) {
                        if (task.getCompleted() == null) {
                            // Active task
                            activeTasks.add(new TaskItem(task));
                        } else if (!hideCompleted) {
                            // Completed task
                            completedTasks.add(new TaskItem(task));
                        }
                    }

                    Collections.sort(activeTasks);
                    taskItems.addAll(activeTasks);

                    if (!hideCompleted) {
                        Collections.sort(completedTasks, TaskItem.completionDateComparator);
                        if (completedTasks.size() > 0) {
                            taskItems.add(new CategoryItem().withName(R.string.completed));
                            taskItems.addAll(completedTasks);
                        }
                    }

                    return taskItems;
                });
    }

    public static Transformer<List<TaskList>, List<IItem>> getTaskListItems() {
        return observable -> observable
                .map(taskLists -> {
                    List<IItem> taskListItems = new ArrayList<>(taskLists.size());

                    for (int i = 0, taskListsSize = taskLists.size(); i < taskListsSize; i++) {
                        TaskList taskList = taskLists.get(i);
                        taskListItems.add(new TaskListItem(taskList));
                    }

                    return taskListItems;
                });
    }
}
