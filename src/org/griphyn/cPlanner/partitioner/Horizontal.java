/*
 * This file or a portion of this file is licensed under the terms of
 * the Globus Toolkit Public License, found in file GTPL, or at
 * http://www.globus.org/toolkit/download/license.html. This notice must
 * appear in redistributions of this file, with or without modification.
 *
 * Redistributions of this Software, with or without modification, must
 * reproduce the GTPL in: (1) the Software, or (2) the Documentation or
 * some other similar material which is provided with the Software (if
 * any).
 *
 * Copyright 1999-2004 University of Chicago and The University of
 * Southern California. All rights reserved.
 */
package org.griphyn.cPlanner.partitioner;

import org.griphyn.cPlanner.common.LogManager;
import org.griphyn.cPlanner.common.PegasusProperties;

import org.griphyn.cPlanner.partitioner.graph.GraphNode;
import org.griphyn.cPlanner.partitioner.graph.Bag;
import org.griphyn.cPlanner.partitioner.graph.LabelBag;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

/**
 * Horizontal based partitioning scheme, that allows the user to configure the
 * number of partitions per transformation name per level.
 * To set the size of the partition per transformation, the following properties
 * need to be set
 * <pre>
 *       pegasus.partitioner.horizontal.collapse.[txName]
 *       pegasus.partitioner.horizontal.bundle.[txName]
 * </pre>
 *
 * The bundle value designates the number of partitions per transformation per level.
 * The collapse values designates the number of nodes in a partitioning referring
 * to a particular transformation. If both are specified, then bundle value takes
 * precedence.
 *
 * @author Karan Vahi
 * @version $Revision$
 */
public class Horizontal extends BFS{

    /**
     * A short description about the partitioner.
     */
    public static final String DESCRIPTION = "Configurable Level Based Partitioning";

    /**
     * The default collapse factor for collapsing jobs with same logical name
     * scheduled onto the same execution pool.
     */
    public static final int DEFAULT_COLLAPSE_FACTOR = 3;

    /**
     * A map indexed by the partition ID. Each value is a partition object.
     */
    private Map mPartitionMap;


    /**
     * A static instance of GraphNode comparator.
     */
    private GraphNodeComparator mNodeComparator;


    /**
     * The global counter that is used to assign ID's to the partitions.
     */
    private int mIDCounter;


    /**
     * Singleton access to the job comparator.
     *
     * @return the job comparator.
     */
    private Comparator nodeComparator(){
        return (mNodeComparator == null)?
                new GraphNodeComparator():
                mNodeComparator;
    }


    /**
     * The overloaded constructor.
     *
     * @param root   the dummy root node of the graph.
     * @param graph  the map containing all the nodes of the graph keyed by
     *               the logical id of the nodes.
     * @param properties the properties passed to the planner.
     */
    public Horizontal(GraphNode root, Map graph, PegasusProperties properties) {
        super(root,graph,properties);
        mIDCounter = 0;
        mPartitionMap = new HashMap( 10 );
    }



    /**
     * Returns a textual description of the partitioner implementation.
     *
     * @return a short textual description
     */
    public String description(){
        return this.DESCRIPTION;
    }


    /**
     * Given a list of jobs, constructs (one or more) partitions out of it.
     * Calls out to the partitioner callback, for each of the partitions
     * constructed.
     *
     * @param c     the parititoner callback
     * @param nodes the list of <code>GraphNode</code> objects on a particular level.
     * @param level the level as determined from the root of the workflow.
     */
    protected void constructPartitions( Callback c, List nodes, int level ){
        //group the nodes by their logical names
        Collections.sort( nodes, nodeComparator() );
        //traverse through the list and collapse jobs
        //referring to same logical transformation
        GraphNode previous = null;
        List clusterList = new LinkedList();
        GraphNode node = null;

        for(Iterator it = nodes.iterator();it.hasNext();){
           node = (GraphNode)it.next();
           if( previous == null ||  node.getName().equals( previous.getName() ) ){
               clusterList.add( node );
           }
           else{
               //at boundary collapse jobs
               constructPartitions( c, clusterList, level, previous.getName() );
               clusterList = new LinkedList();
               clusterList.add( node );
           }
           previous = node;
       }
       //cluster the last clusterList
       if(previous != null){
           constructPartitions( c, clusterList, level, previous.getName() );
       }


    }

    /**
     * Given a list of jobs, constructs (one or more) partitions out of it.
     * Calls out to the partitioner callback, for each of the partitions
     * constructed.
     *
     * @param c     the parititoner callback
     * @param nodes the list of <code>GraphNode</code> objects on a particular level,
     *              referring to the same transformation underneath.
     * @param level the level as determined from the root of the workflow.
     * @param name  the transformation name
     */
    protected void constructPartitions( Callback c, List nodes, int level, String name ){
        //figure out number of jobs that go into one partition
        int[] cFactor  = new int[2];
        cFactor[0]     = 0;
        cFactor[1]     = 0;

        int size = nodes.size();
        cFactor = this.getCollapseFactor( name, size );

        StringBuffer message = new StringBuffer();

        if( cFactor[0] == 0 && cFactor[1] == 0 ){
            message.append( "\t Collapse factor of ").append( cFactor[0] ).
                    append( "," ).append( cFactor[1] ).
                    append( " determined for transformation ").append( name );
            mLogger.log( message.toString(),  LogManager.DEBUG_MESSAGE_LEVEL );
            return;
        }

        message.append( "Partitioning jobs of type " ).append( name ).append( " at level ").
                append( level ).append(" wth collapse factor ").
                append( cFactor[0] ).append( "," ).append( cFactor[1] );

        mLogger.log( message.toString(), LogManager.DEBUG_MESSAGE_LEVEL );

        Partition p;
        if( cFactor[0] >= size ){
            //means put all the nodes in one partition
            //we want to ignore the dummy node partition
            p = createPartition( nodes );
            c.cbPartition( p );
        }
        else{
            //do collapsing in chunks of cFactor
            int increment = 0;
            int toIndex;
            for( int i = 0; i < size; i = i + increment ){
                //compute the increment and decrement cFactor[1]
                increment = (cFactor[1] > 0) ? cFactor[0] + 1: cFactor[0];
                cFactor[1]--;

                //determine the toIndex for creating the partition
                toIndex =  ( (i + increment) < size) ?
                             i + increment :
                             size;

                p = createPartition( nodes.subList( i, toIndex ) );
                c.cbPartition( p );
            }
        }


    }

    /**
     * Calls out to the callback with appropriate relations between the partitions
     * constructed for the levels. This is an empty implementation, as we
     * do our own book-keeping in this partitioner to determine the relations
     * between the partitions.
     *
     * @param c       the parititoner callback
     * @param parent  the parent level
     * @param child   the child level.
     *
     * @see #done( Callback )
     */
    protected void constructLevelRelations( Callback c, int parent, int child ){


    }

    /**
     * Indicates that we are done with the traversal of the graph. Determines
     * the relations between the partitions constructed and calls out to the
     * appropriate callback function
     *
     * @param c  the partitioner callback
     */
    protected void done( Callback c ){
        GraphNode node;
        GraphNode parent;

        mLogger.log( "Determining relations between partitions",
                     LogManager.INFO_MESSAGE_LEVEL );
        //construct the relations
        for( Iterator it = mPartitionMap.entrySet().iterator(); it.hasNext(); ){
            Map.Entry entry = (Map.Entry) it.next();
            Partition p = (Partition) entry.getValue();
            List roots = p.getRootNodes();
            Set parentPartitions = new HashSet( roots.size() );

            //get the Root nodes for each partition and
            //for each root, determine the partitions of it's parents
            for( Iterator rootIt = roots.iterator(); rootIt.hasNext(); ){
                node = (GraphNode)rootIt.next();
                for( Iterator parentsIt = node.getParents().iterator(); parentsIt.hasNext(); ){
                    parent = (GraphNode)parentsIt.next();
                    //the parents partition id is parent for the
                    //partition containing the root
                    parentPartitions.add( parent.getBag().get( LabelBag.PARTITION_KEY ) );
                }
            }
            //write out all the parents of the partition
            if(!parentPartitions.isEmpty()){
                c.cbParents( p.getID(), new ArrayList( parentPartitions ) );
            }
        }
        mLogger.logCompletion( "Determining relations between partitions",
                               LogManager.INFO_MESSAGE_LEVEL );


        //done with the partitioning
        c.cbDone();
    }



    /**
     * Returns the collapse factor, that is used to determine the number of nodes
     * going in a partition. The collapse factor is determined by
     * getting the collapse and the bundle values specified for the transformations
     * in the properties file.
     *
     * There are two orthogonal notions of bundling and collapsing. In case the
     * bundle key is specified, it ends up overriding the collapse key, and
     * the bundle value is used to generate the collapse values.
     *
     * If both are not specified or null, then collapseFactor is set to size.
     *
     * @param txName the logical transformation name
     * @param size  the number of jobs that refer to the same logical
     *              transformation and are scheduled on the same execution pool.
     *
     * @return int array of size 2 where :-
     *         int[0] is the the collapse factor  (number of nodes in a partition)
     *         int[1] is the number of parititons for whom collapsing is int[0] + 1.
     */
    protected int[] getCollapseFactor(String txName, int size){
        String factor = null;
        String bundle = null;
        int result[]  = new int[2];
        result[1]     = 0;

        //the job should have the collapse key from the TC if
        //by the user specified
        try{
            //ceiling is (x + y -1)/y
            bundle = mProps.getHorizontalPartitionerBundleValue(txName);
            if (bundle != null) {
                int b = Integer.parseInt(bundle);
                result[0] = size / b;
                result[1] = size % b;
                return result;
                //doing no boundary condition checks
                //return (size + b -1)/b;
            }

            factor = mProps.getHorizontalPartitionerCollapseValue(txName);
            //return the appropriate value
            result[0] = (factor == null) ?
                size : //then collapse factor is same as size
                Integer.parseInt(factor); //use the value in the prop file
        }
        catch( NumberFormatException e ){
            //set bundle to size
            StringBuffer error = new StringBuffer();

            if( factor == null ){
                error.append( "Bundle value (" ).append( bundle ).append( ")" );
            }
            else{
               error.append( "Collapse value (").append(factor).append( ")" );
            }
            error.append( " for transformation ").
                  append( txName ).append(" is not a number" );
            mLogger.log( error.toString(), LogManager.DEBUG_MESSAGE_LEVEL );
            result[0] = size;
        }
        return result;

    }


    /**
     * Creates a partition out of a list of nodes. Also stores it in the internal
     * partition map to track partitions later on. Associates the partition ID
     * with each of the nodes making the partition also.
     *
     * @param nodes  the list of <code>GraphNodes</code> making the partition.
     *
     * @return the partition out of those nodes.
     */
    protected Partition createPartition( List nodes ){
        //increment the ID counter before getting the ID
        this.incrementIDCounter();
        String id = getPartitionID( this.idCounter() );
        Partition p = new Partition( nodes, id );
        p.setIndex( this.idCounter() );
        p.constructPartition();

        mPartitionMap.put( p.getID(), p );

        //associate the ID with all the nodes
        for( Iterator it = nodes.iterator(); it.hasNext(); ){
            GraphNode node = (GraphNode)it.next();
            Bag b = new LabelBag();
            b.add( LabelBag.PARTITION_KEY, id );
            node.setBag( b );
        }

        //log a message
        StringBuffer message = new StringBuffer();
        message.append( "Partition " ).append( p.getID() ).append(" is :").
            append( p.getNodeIDs() );
        mLogger.log( message.toString(), LogManager.DEBUG_MESSAGE_LEVEL );

        return p;
    }

    /**
     * Increments the ID counter by 1.
     */
    private void incrementIDCounter(){
        mIDCounter++;
    }


    /**
     * Returns the current value of the ID counter.
     */
    private int idCounter(){
       return mIDCounter;
    }


    /**
     * Constructs the id for the partition.
     *
     * @param id an integer ID.
     *
     * @return the ID for the Partition.
     */
    private String getPartitionID( int id ){
        StringBuffer sb = new StringBuffer(5);
        sb.append("ID").append( id );
        return sb.toString();
    }


    /**
     * A GraphNode comparator, that allows me to compare nodes according to the
     * transformation logical names. It is applied to group jobs in a particular partition,
     * according to the underlying transformation that is referred.
     *
     */
   private class GraphNodeComparator implements Comparator{

       /**
         * Compares this object with the specified object for order. Returns a
         * negative integer, zero, or a positive integer if the first argument is
         * less than, equal to, or greater than the specified object. The
         * SubInfo are compared by their transformation name.
         *
         * This implementation is not consistent with the
         * SubInfo.equals(Object) method. Hence, should not be used in sorted
         * Sets or Maps.
         *
         * @param o1 is the first object to be compared.
         * @param o2 is the second object to be compared.
         *
         * @return a negative number, zero, or a positive number, if the
         * object compared against is less than, equals or greater than
         * this object.
         * @exception ClassCastException if the specified object's type
         * prevents it from being compared to this Object.
         */
        public int compare(Object o1, Object o2) {
            if (o1 instanceof  GraphNode && o2 instanceof  GraphNode) {
                return ( ( GraphNode) o1).getName().compareTo( ( (
                     GraphNode) o2).getName());

            }
            else {
                throw new ClassCastException("Objects being compared are not  GraphNode");
            }
        }
   }


}