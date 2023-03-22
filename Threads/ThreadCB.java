package osp.Threads;
import java.util.Vector;
import java.util.Enumeration;
import osp.Utilities.*;
import osp.IFLModules.*;
import osp.Tasks.*;
import osp.EventEngine.*;
import osp.Hardware.*;
import osp.Devices.*;
import osp.Memory.*;
import osp.Resources.*;

/**
   This class is responsible for actions related to threads, including
   creating, killing, dispatching, resuming, and suspending threads.

   @OSPProject Threads
*/
public class ThreadCB extends IflThreadCB 
{
    public static Vector<ThreadCB> RQ;
    /**
       The thread constructor. Must call 

       	   super();

       as its first statement.

       @OSPProject Threads
    */
    public ThreadCB()
    {
    	super(); // calling parent constructor
    }

    /**
       This method will be called once at the beginning of the
       simulation. The student can set up static variables here.
       
       @OSPProject Threads
    */
    public static void init()
    {
        RQ = new Vector<ThreadCB>();
    }

    /** 
        Sets up a new thread and adds it to the given task. 
        The method must set the ready status 
        and attempt to add thread to task. If the latter fails 
        because there are already too many threads in this task, 
        so does this method, otherwise, the thread is appended 
        to the ready queue and dispatch() is called.

	The priority of the thread can be set using the getPriority/setPriority
	methods. However, OSP itself doesn't care what the actual value of
	the priority is. These methods are just provided in case priority
	scheduling is required.

	@return thread or null

        @OSPProject Threads
    */
    static public ThreadCB do_create(TaskCB task)
    {
        if(task.getThreadCount() == MaxThreadsPerTask || task == null) { // check to see if the task has max #
		dispatch(); // calling the dispatcher
		return null;
	}
	ThreadCB T = new ThreadCB(); // creating thread t
	T.setStatus(ThreadReady); // setting status of t to ready
	T.setTask(task); // associating t with task
	task.addThread(T); // associating task with thread
	if(task.getThreadCount() == 0) {
		dispatch();
		return null;
	}
	ThreadCB.RQ.add(T); // adding thread to the ready queue
	dispatch(); // dispatch
	return T; // return
    }

    /** 
	Kills the specified thread. 

	The status must be set to ThreadKill, the thread must be
	removed from the task's list of threads and its pending IORBs
	must be purged from all device queues.
        
	If some thread was on the ready queue, it must removed, if the 
	thread was running, the processor becomes idle, and dispatch() 
	must be called to resume a waiting thread.
	
	@OSPProject Threads
    */
    public void do_kill()
    {
	if(this.getStatus() == ThreadReady) { // check status of thread, runs if status is ready
		ThreadCB.RQ.remove(this); // remove from queue if in ready status
	}
	if(this.getStatus() == ThreadRunning) { // check status of thread, runs if status is running, preempting
		MMU.getPTBR().getTask().getCurrentThread().setStatus(ThreadWaiting); // status to waiting
		MMU.setPTBR(null); // set PTBR to null
		this.getTask().setCurrentThread(null); // telling task thread is not running
	}
	this.setStatus(ThreadKill); // setting status to kill
	for(int i = 0; i < Device.getTableSize(); i++) { // purging IORB associated with thread
		Device.get(i).cancelPendingIO(this);
	}
	ResourceCB.giveupResources(this); //  releasing reasources
	this.getTask().removeThread(this); // removing thread from task 
	if(this.getTask().getThreadCount() == 0) // checking if task has other threads
		this.getTask().kill(); // if not kills task
	dispatch(); // dispatch
    }

    /** Suspends the thread that is currenly on the processor on the 
        specified event. 

        Note that the thread being suspended doesn't need to be
        running. It can also be waiting for completion of a pagefault
        and be suspended on the IORB that is bringing the page in.
	
	Thread's status must be changed to ThreadWaiting or higher,
        the processor set to idle, the thread must be in the right
        waiting queue, and dispatch() must be called to give CPU
        control to some other thread.

	@param event - event on which to suspend this thread.

        @OSPProject Threads
    */
    public void do_suspend(Event event)
    {
	if(this.getStatus() >= ThreadWaiting) // determines status of thread, runs if status is waiting or higher
		this.setStatus(this.getStatus()+1); // increments status 
	ThreadCB.RQ.remove(this); // making sure thread is not in ready queue
	event.addThread(this); // add thread to event queue
	dispatch(); // dispatch
	if(this.getStatus() == ThreadRunning) { // determines status of thread, runs if status is running
		this.setStatus(ThreadWaiting); // changes status to waiting
		MMU.setPTBR(null); // PTBR to null
		this.getTask().setCurrentThread(null); // current thread of task to null 
	}

    }

    /** Resumes the thread.
        
	Only a thread with the status ThreadWaiting or higher
	can be resumed.  The status must be set to ThreadReady or
	decremented, respectively.
	A ready thread should be placed on the ready queue.
	
	@OSPProject Threads
    */
    public void do_resume()
    {
	if(this.getStatus() == ThreadRunning) { // determines status of thread
		dispatch(); // runs if status is running, dispatches
		return; // returns
	} else if(this.getStatus() == ThreadWaiting) { // runs if status is waiting
		this.setStatus(ThreadReady); // set status to ready
		ThreadCB.RQ.add(this); // adds thread to queue
		dispatch(); // dispatch
	} else if(this.getStatus() == ThreadReady) { // runs is status is waiting 
		ThreadCB.RQ.add(this); // adds thread to queue
		dispatch();
	}
	else { // any other status
		this.setStatus(this.getStatus() - 1); // decrements status by one
		dispatch(); // dispatch
	}
    }

    /** 
        Selects a thread from the run queue and dispatches it. 

        If there is just one theread ready to run, reschedule the thread 
        currently on the processor.

        In addition to setting the correct thread status it must
        update the PTBR.
	
	@return SUCCESS or FAILURE

        @OSPProject Threads
    */
    public static int do_dispatch()
    {
        try { // check if thread already running
		MMU.getPTBR().getTask().getCurrentThread(); // true if thread running
		return SUCCESS;	// return success
	} catch(Exception e) { // no thread running
		if(ThreadCB.RQ.isEmpty()) { // true if RQ is empty 
			MMU.setPTBR(null); // if empty sets PTBR to null
		        // this is loading a idle process
			return FAILURE; // return failure
		} // next code segment only runs if RQ is NOT empty
		ThreadCB T = ThreadCB.RQ.firstElement(); // copies first element of ready queue
		ThreadCB.RQ.remove(T); // removes first element
		T.setStatus(ThreadRunning); // sets status of T to running
		MMU.setPTBR(T.getTask().getPageTable()); // sets PTBR to the new threads PTBR
		T.getTask().setCurrentThread(T); // sets the task of threads current taks to new thread
		return SUCCESS; // returns success
	}
    }

    /**
       Called by OSP after printing an error message. The student can
       insert code here to print various tables and data structures in
       their state just after the error happened.  The body can be
       left empty, if this feature is not used.

       @OSPProject Threads
    */
    public static void atError()
    {
        // your code goes here
    }

    /** Called by OSP after printing a warning message. The student
        can insert code here to print various tables and data
        structures in their state just after the warning happened.
        The body can be left empty, if this feature is not used.
       
        @OSPProject Threads
     */
    public static void atWarning()
    {
        // your code goes here

    }


    /*
       Feel free to add methods/fields to improve the readability of your code
    */

}

/*
      Feel free to add local classes to improve the readability of your code
*/
