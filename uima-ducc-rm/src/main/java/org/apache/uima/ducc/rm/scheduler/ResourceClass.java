/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
*/
package org.apache.uima.ducc.rm.scheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import org.apache.uima.ducc.common.utils.DuccLogger;
import org.apache.uima.ducc.common.utils.DuccProperties;
import org.apache.uima.ducc.common.utils.SystemPropertyResolver;


/**
 * This represents a priority class.
 */
public class ResourceClass
    implements SchedConstants,
               IEntity
{
    private static DuccLogger logger = DuccLogger.getLogger(ResourceClass.class, COMPONENT_NAME);

    private String id;
    private Policy policy;
    private int priority;           // orders evaluation of the class

    private int share_weight;       // for fair-share, the share weight to use

    private int share_quantum;      // for limits, to convert shares to GB
    private int max_allotment;      // All allocation policies, max in GB
    private int max_processes;      // fixed-share: max shares to hand out regardless of
                                    // what is requested or what fair-share turns out to be

    // for shares, this caps shares
    private int absolute_cap;       // max shares or machines this class can hand out
    private double percent_cap;     // max shares or machines this class can hand out as a percentage of all shares
    private int true_cap;           // set during scheduling, based on actual current resource availability
    private int pure_fair_share;    // the unmodified fair share, not counting caps, and not adding in bonuses

    private Map<String, String> authorizedUsers = new HashMap<String, String>();      // if non-empty, restricted set of users
                                                                                      // who can use this class
    private HashMap<IRmJob, IRmJob>                   allJobs = new HashMap<IRmJob, IRmJob>();
    private HashMap<Integer, HashMap<IRmJob, IRmJob>> jobsByOrder = new HashMap<Integer, HashMap<IRmJob, IRmJob>>();
    private HashMap<User, HashMap<IRmJob, IRmJob>>    jobsByUser = new HashMap<User, HashMap<IRmJob, IRmJob>>();
    private int max_job_order = 0;  // largest order of any job still alive in this rc (not necessarily globally though)

    private NodePool nodepool = null;

    // private HashMap<Integer, Integer> nSharesByOrder = new HashMap<Integer, Integer>();         // order, N shares of that order
    private boolean subpool_counted = false;

    // the physical presence of nodes in the pool is somewhat dynamic - we'll store names only, and generate
    // a map of machines on demand by the schedler from currently present machnes
    private String nodepoolName = null;

//     ArrayList<String> nodepool = new ArrayList<String>();                               // nodepool names only
//     HashMap<String, Machine> machinesByName = new HashMap<String, Machine>();
//     HashMap<String, Machine> machinesByIp = new HashMap<String, Machine>();

    // Whether to enforce memory constraints for matching reservations
    private boolean enforce_memory = true;

    // int class_shares;       // number of shares to apportion to jobs in this class in current epoch

    private boolean expand_by_doubling = SystemPropertyResolver.getBooleanProperty("ducc.rm.expand.by.doubling", true);
    private int initialization_cap = SystemPropertyResolver.getIntProperty("ducc.rm.initialization.cap", 2);
    private long prediction_fudge = SystemPropertyResolver.getIntProperty("ducc.rm.prediction.fudge", 60000);
    private boolean use_prediction = SystemPropertyResolver.getBooleanProperty("ducc.rm.prediction", true);
    
    private int[] given_by_order  = null;
    private int[] wanted_by_order = null;               // volatile - changes during countClassesByOrder

    private static Comparator<IEntity> apportionmentSorter = new ApportionmentSorterCl();

    public ResourceClass(DuccProperties props, long share_quantum)
    {
        //
        // We can assume everything useful is here because the parser insured it
        //
        this.id = props.getStringProperty("name");
        this.policy = Policy.valueOf(props.getStringProperty("policy"));
        this.priority = props.getIntProperty("priority");
        this.share_quantum = (int) (share_quantum / ( 1024 * 1024 ));        // KB back to GB

        String userset = props.getProperty("users");
        if ( userset != null ) {
            String[] usrs = userset.split("\\s+");
            for ( String s : usrs ) {
                authorizedUsers.put(s, s);
            }
        }

        this.max_allotment = props.getIntProperty("max-allotment", Integer.MAX_VALUE);

        if ( policy == Policy.RESERVE ) {
            this.enforce_memory = props.getBooleanProperty("enforce", true);
        }

        if ( policy != Policy.RESERVE ) {
            this.max_processes = props.getIntProperty("max-processes", Integer.MAX_VALUE);
        }

        this.absolute_cap = Integer.MAX_VALUE;
        this.percent_cap  = 1.0;

        String cap  = props.getStringProperty("cap");
        if ( cap.endsWith("%") ) {
            int t = Integer.parseInt(cap.substring(0, cap.length()-1));
            this.percent_cap = (t * 1.0 ) / 100.0;
        } else {
            absolute_cap = Integer.parseInt(cap);
            if (absolute_cap == 0) absolute_cap = Integer.MAX_VALUE;
        }

        if ( this.policy == Policy.FAIR_SHARE ) {
            this.share_weight = props.getIntProperty("weight");
            if ( props.containsKey("expand-by-doubling") ) {
                this.expand_by_doubling = props.getBooleanProperty("expand-by-doubling", true);
            } else {
                this.expand_by_doubling  = SystemPropertyResolver.getBooleanProperty("ducc.rm.expand.by.doubling", true);
            }
            
            if ( props.containsKey("initialization-cap") ) {
                this.initialization_cap = props.getIntProperty("initialization-cap");
            } else {
                this.initialization_cap  = SystemPropertyResolver.getIntProperty("ducc.rm.initialization.cap", 2);
            }
            
            if ( props.containsKey("use-prediction") ) {
                this.use_prediction = props.getBooleanProperty("use-prediction", true);
            } else {
                this.use_prediction = SystemPropertyResolver.getBooleanProperty("ducc.rm.prediction", true);
            }
            
            if ( props.containsKey("prediction-fudge") ) {
                this.prediction_fudge = props.getLongProperty("prediction-fudge");
            } else {
                this.prediction_fudge  = SystemPropertyResolver.getLongProperty("ducc.rm.prediction.fudge", 60000);
            }
        }

        this.nodepoolName = props.getStringProperty("nodepool");
                                                                        
    }

    public boolean authorized(String user)
    {
        if ( authorizedUsers.size() == 0 ) return true;
        return authorizedUsers.containsKey(user);
    }

    public void setNodepool(NodePool np)
    {
        this.nodepool = np;
    }

    public NodePool getNodepool()
    {
        return this.nodepool;
    }

    public long getTimestamp()
    {
        return 0;
    }

    String getNodepoolName()
    {
        return nodepoolName;
    }

    public void setPureFairShare(int pfs)
    {
        this.pure_fair_share = pfs;
    }

    public int getPureFairShare()
    {
        return pure_fair_share;
    }

    public boolean isExpandByDoubling()
    {
        return expand_by_doubling;
    }

    public void setExpandByDoubling(boolean ebd)
    {
        this.expand_by_doubling = ebd;
    }

    public int getInitializationCap()
    {
        return initialization_cap;
    }

    public void setInitializationCap(int c)
    {
        this.initialization_cap = c;
    }

    public boolean isUsePrediction()
    {
        return use_prediction;
    }

    public long getPredictionFudge()
    {
        return prediction_fudge;
    }

    public boolean enforceMemory()
    {
        return enforce_memory;
    }

    public Policy getPolicy()
    {
        return policy;
    }

    public void setTrueCap(int cap)
    {
        this.true_cap = cap;
    }

    public int getTrueCap()
    {
        return true_cap;
    }

    public double getPercentCap() {
        return percent_cap;
    }

    public int getAbsoluteCap() {
        return absolute_cap;
    }
        
    public int getMaxProcesses() {
        return max_processes;
    }

    public int getAllotment(IRmJob j) 
    {
        User u = j.getUser();
        int max = u.getClassLimit(this);
        if ( max == Integer.MAX_VALUE ) {
            return max_allotment;       // no user override
        } else {
            return max;
        }
    }
    
    void setPolicy(Policy p)
    {
        this.policy = p;
    }

    /**
    public String getId()
    {
        return id;
    }
*/
 
    public String getName()
    {
        return id;
    }

    public int getShareWeight()
    {
        return share_weight;
    }

    /**
     * See if the total memory for job 'j' plus the occupancy of the 'jobs' exceeds 'max'
     * Returns 'true' if occupancy is exceeded, else returns 'false'
     * UIMA-4275
     */
    private boolean occupancyExceeded(int max, IRmJob j, Map<IRmJob, IRmJob> jobs)
    {
        int occupancy = 0;
        for ( IRmJob job : jobs.values() ) {
            if ( ! job.getUserName().equals(j.getUserName()) ) continue;           // limits are user based

            // nshares_given is shares counted out for the job but maybe not assigned
            // nshares       is shares given
            // share_order   is used to convert nshares to qshares so
            // so ( nshares_give + nshares ) * share_order is the current potential occupancy of the job
            // Then multiply by the scheduling quantum to convert to GB
            occupancy += ( job.countNSharesGiven()  * job.getShareOrder() * share_quantum ); // convert to GB
        }
        int requested = j.getMemory() * j.countInstances();
        
        if ( max - ( occupancy + requested ) < 0 ) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Does this job push the per-user allotment over the top?
     *
     * Note that we don't store current occupancy directly, we always calculate it from the
     * jobs assigned to the class.  Less bookkeeping that way.
     * UIMA-4275
     */
    public boolean allotmentExceeded(IRmJob j)
    {
        User u = j.getUser();
        int max = u.getClassLimit(this);

        switch ( policy ) {
            case FIXED_SHARE:
            case RESERVE:
            {
                if ( max != Integer.MAX_VALUE ) {
                    // user is constrained, and the constraint overrides the class constraint
                    return occupancyExceeded(max, j, jobsByUser.get(j.getUser()));
                } else {
                    // user is not constrained.  check class constraints
                    if ( max_allotment == Integer.MAX_VALUE ) return false;   // no class constraints

                    return occupancyExceeded(max_allotment, j, allJobs);
                }
            }

            // for completion of the case - this is handled elsewhere
            case FAIR_SHARE:
            default:
                return false;            
        }
    }

    /**
     * Return my share weight, if I have any jobs of the given order or less.  If not,
     * return 0;
     */
    public int getEffectiveWeight(int order)
    {
        for ( int o = order; o > 0; o-- ) {
            if ( jobsByOrder.containsKey(o) && ( jobsByOrder.get(o).size() > 0) ) {
                return share_weight;
            }
        }
        return 0;
    }

    /**
     * Can I use more 1 more share of this size?  This is more complex than for Users and Jobs because
     * in addition to checking if my request is filled, we need to make sure the underlying nodepools
     * can support the bonus.  (This creates an upper bound on apportionment from this class that tends
     * to trickle down into users and jobs as the counting progresses).
     * UIMA-4065
     *
     * @param order The size of the available share.  Must be an exact match because the
     *              offerer has already done all reasonable splitting and will have a better
     *              use for it if I can't take it.
     *
     *              The decision is based on the wbo/gbo arrays that the offer has been building up
     *              just before asking this question.
     *
     * @return      True if I can use the share, false otherwise.
     */
    public boolean canUseBonus(int order)              // UIMA-4065
    {
        String methodName = "canUseBonus";
        int wbo = getWantedByOrder()[order];           // calculated by caller so we don't need to check caps
        int gbo = getGivenByOrder()[order];

        // 
        // we want to ask the nodepool and its subpools:
        //    how many open shares of "order" will you have after we give way
        //    the ones already counte?
        //
        //  To do this, we have "our" nodepool recursively gather all thear classes
        //  and  accumulate this:  np.countLocalNSharesByOrder - (foreachrc: gbo[order])
        //
        //  Then, if gbo < resourcesavailable we can return true, else return false
        //
        int resourcesAvailable = nodepool.countAssignableShares(order);      // recurses, covers all relevent rc's
        logger.info(methodName, null, "Class", id, "nodepool", nodepool.getId(), "order", order, "wbo", wbo, "gbo", gbo, "resourcesAvailable", resourcesAvailable);

        if ( wbo <= 0 ) return false;

        if ( resourcesAvailable <= 0 ) {          // if i get another do I go over?
            return false;                              // yep, politely decline
        }
        return true;                           
   }

    void updateNodepool(NodePool np)
    {
        //String methodName = "updateNodepool";

        if ( given_by_order == null ) return;       // nothing given, nothing to adjust

        for ( int o = NodePool.getMaxOrder(); o > 0; o-- ) {
            np.countOutNSharesByOrder(o, given_by_order[o]);
        }
    }
    
    public int getPriority()
    {
    	return priority;
    }
    
    public void clearShares()
    {
        //class_shares = 0;
        given_by_order = null;
        subpool_counted = false;
    }
    
    public void markSubpoolCounted()
    {
        subpool_counted = true;
    }

    void addJob(IRmJob j)
    {
        allJobs.put(j, j);

        int order = j.getShareOrder();
        HashMap<IRmJob, IRmJob> jbo = jobsByOrder.get(order);
        if ( jbo == null ) {
            jbo = new HashMap<IRmJob, IRmJob>();
            jobsByOrder.put(order, jbo);
            max_job_order = Math.max(max_job_order, order);
        }
        jbo.put(j, j);

        User u = j.getUser();
        jbo = jobsByUser.get(u);
        if ( jbo == null ) {
            jbo = new HashMap<IRmJob, IRmJob>();
            jobsByUser.put(u, jbo);
        }
        jbo.put(j, j);

    }

    void removeJob(IRmJob j)
    {
        if ( ! allJobs.containsKey(j) ) {
            if ( j.isRefused() ) return;

            throw new SchedulingException(j.getId(), "Priority class " + getName() + " cannot find job to remove.");
        }

        allJobs.remove(j);

        int order = j.getShareOrder();
        HashMap<IRmJob, IRmJob> jbo = jobsByOrder.get(order);
        jbo.remove(j);
        if ( jbo.size() == 0 ) {
            jobsByOrder.remove(order);

            for ( int o = order - 1; o > 0; o-- ) {
                if ( jobsByOrder.containsKey(o) ) {
                    max_job_order = o;
                    break;
                }
            }
        }

        User u = j.getUser();
        jbo = jobsByUser.get(u);
        jbo.remove(j);
        if ( jbo.size() == 0 ) {
            jobsByUser.remove(u);
        }
    }

    int countJobs()
    {
        return allJobs.size();
    }

    /**
     * Returns total N-shares wanted by order. Processes of size order.
     */
    private int countNSharesWanted(int order)
    {
        int K = 0;
        
        // First sum the max shares all my jobs can actually use
        HashMap<IRmJob, IRmJob> jobs = jobsByOrder.get(order);
        if ( jobs == null ) {
            return 0;
        }

        for ( IRmJob j : jobs.values() ) {
            K += j.getJobCap();
        }

        return K;
    }

    public void initWantedByOrder(ResourceClass unused)
    {
        int ord = NodePool.getMaxOrder();
        wanted_by_order = NodePool.makeArray();
        for ( int o = ord; o > 0; o-- ) {
            wanted_by_order[o] = countNSharesWanted(o);
            wanted_by_order[0] += wanted_by_order[o];
        }
    }

    public int[] getWantedByOrder()
    {
        return wanted_by_order;
    }

    public int[] getGivenByOrder()
    {
    	return given_by_order;
    }

    public void setGivenByOrder(int[] gbo)
    {
        if ( given_by_order == null ) {      // Can have multiple passes, don't reset on subsequent ones.
            this.given_by_order = gbo;       // Look carefuly at calculateCap() below for details.
        }
    }

    public int calculateCap(int order, int basis)
    {
        int perccap = Integer.MAX_VALUE;    // the cap, calculated from percent
        int absolute = getAbsoluteCap();
        double percent = getPercentCap();

        if ( percent < 1.0 ) {
            double b = basis;
            b = b * percent;
            perccap = (int) Math.round(b);
        } else {
            perccap = basis;
        }

        int cap =  Math.min(absolute, perccap) / order;   // cap on total shares available

        //
        // If this RC is defined over a nodepool that isn't the global nodepool then its share
        // gets calculated multiple times.  The first time when it is encountered during the
        // depth-first traversal, to work out the fair-share for all classes defined over the
        // nodepool.  Subpool resources are also available to parent pools however, and must
        // be reevaluated to insure the resources are fairly allocated over the larger pool
        // of work.
        //
        // So at this point there might already be shares assigned.  If so, we need to
        // recap on whatever is already given to avoid over-allocating "outside" of the
        // assigned shares.
        //
        if ( (given_by_order != null) && subpool_counted )  {
            cap = Math.min(cap, given_by_order[order]);
        } // else - never been counted or assigned at this order, no subpool cap
        
        return cap;
    }


    public boolean hasSharesGiven() 
    {
        return ( (given_by_order != null) && (given_by_order[0] > 0) );
    }

    private int countActiveShares()
    {
        int sum = 0;
        for ( IRmJob j : allJobs.values() ) {
            sum += (j.countNShares() * j.getShareOrder());          // in quantum shares
        }
        return sum;
    }

    HashMap<IRmJob, IRmJob> getAllJobs()
    {
        return allJobs;
    }

    HashMap<Integer, HashMap<IRmJob, IRmJob>> getAllJobsByOrder()
    {
        return jobsByOrder;
    }

    HashMap<User, HashMap<IRmJob, IRmJob>> getAllJobsByUser()
    {
        return jobsByUser;
    }

    ArrayList<IRmJob> getAllJobsSorted(Comparator<IRmJob> sorter)
    {
        ArrayList<IRmJob> answer = new ArrayList<IRmJob>();
        answer.addAll(allJobs.values());
        Collections.sort(answer, sorter);
        return answer;
    }

    int getMaxJobOrder()
    {
        return max_job_order;
    }

    int makeReadable(int i)
    {
        return (i == Integer.MAX_VALUE ? -1 : i);
    }
    
    // note we assume Nodepool is the last token so we don't set a len for it!
    private static String formatString = "%12s %11s %4s %5s %5s %6s %6s %7s %6s %6s %7s %5s %7s %s";
    public static String getDashes()
    {
        return String.format(formatString, "------------", "-----------",  "----", "-----", "-----", "------", "------", "-------", "------", "------", "-------", "-----", "-------", "--------");
    }

    public static String getHeader()
    {
        return String.format(formatString, "Class Name", "Policy", "Prio", "Wgt", "MaxSh", "AbsCap", "PctCap", "InitCap", "Dbling", "Prdct", "PFudge", "Shr", "Enforce", "Nodepool");
    }

    @Override
    public int hashCode()
    { 
        return id.hashCode();
    }

    public String toString() {
        return String.format(formatString,
                             id,
                             policy.toString(),
                             priority, 
                             share_weight, 
                             makeReadable(max_processes), 
                             makeReadable(absolute_cap), 
                             (int) (percent_cap *100), 
                             initialization_cap,
                             expand_by_doubling,
                             use_prediction,
                             prediction_fudge,
                             countActiveShares(),
                             enforce_memory,
                             nodepoolName
            );
    }

    public String toStringWithHeader()
    {
        StringBuffer buf = new StringBuffer();
        

        buf.append(getHeader());
        buf.append("\n");
        buf.append(toString());
        return buf.toString();
    }

    public Comparator<IEntity> getApportionmentSorter()
    {
        return apportionmentSorter;
    }

    static private class ApportionmentSorterCl
        implements Comparator<IEntity>
    {
        public int compare(IEntity e1, IEntity e2)
        {
        	// we want a consistent sort, that favors higher share weights
            if ( e1 == e2 ) return 0;
            int w1 = e1.getShareWeight();
            int w2 = e2.getShareWeight();
            if ( w1 == w2 ) {
                return e1.getName().compareTo(e2.getName());
            }
            return (int) (w2 - w1);
        }
    }

}
            
