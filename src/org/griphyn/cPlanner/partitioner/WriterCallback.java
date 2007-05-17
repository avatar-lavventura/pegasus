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

import org.griphyn.cPlanner.common.PegasusProperties;
import org.griphyn.cPlanner.common.LogManager;

import java.util.List;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import java.io.File;
import java.io.IOException;

/**
 * This callback writes out a <code>DAX</code> file for each of the partitions,
 * and also writes out a <code>PDAX</code> file that captures the relations
 * between the partitions.
 *
 * @author not attributable
 * @version $Revision$
 */

public class WriterCallback implements Callback {


    /**
     * The handle to the partition graph writer.
     */
    protected PDAXWriter mPDAXWriter;

    /**
     * The handle to the dax writer that writes out the dax corresponding to the
     * partition identified. The base name of the partition is gotten from it.
     */
    protected DAXWriter mDAXWriter;


    /**
     * Handle to the properties available.
     */
    protected PegasusProperties mProps;

    /**
     * The handle to the logger object.
     */
    protected LogManager mLogger;

    /**
     * A boolean indicating that the partitioning has started. This is set,
     * by the first call to the cbPartition( Partition ) callback.
     */
    protected boolean mPartitioningStarted;

    /**
     * The default constructor.
     *
     */
    public WriterCallback(){
        mLogger = LogManager.getInstance();
    }

    /**
     * Initializes the Writer Callback.
     *
     * @param properties the properties passed to the planner.
     * @param daxFile    the path to the DAX file that is being partitioned.
     * @param daxName    the namelabel of the DAX as set in the root element of the DAX.
     * @param directory  the directory where the partitioned daxes have to reside.
     */
    public void initialize (PegasusProperties properties,
                            String daxFile,
                            String daxName,
                            String directory ){

        mProps = properties;

        //load the writer for the partitioned daxes
        mDAXWriter = DAXWriter.loadInstance( properties, daxFile, directory );
        mDAXWriter.setPartitionName( daxName );

        //name of pdax file is same as the dax file
        //meaning the name attribute in root element are same.
        mPDAXWriter =  getHandletoPDAXWriter( daxFile, daxName, directory ) ;

        //write out the XML header for the PDAX file
        mPDAXWriter.writeHeader();
    }

    /**
     * Callback for when a partitioner determines that partition has been
     * constructed. A DAX file is written out for the partition.
     *
     * @param p the constructed partition.
     *
     * @throws RuntimeException in case of any error while writing out the DAX or
     *         the PDAX files.
     */
    public void cbPartition( Partition p ) {
        mPartitioningStarted = true;

        //not sure if we still need it
        p.setName( mDAXWriter.getPartitionName() );

        //for time being do a localize catch
        //till i change the interface
        try{
            //write out the partition information to the PDAX file
            mLogger.log( "Writing to the pdax file for partition " + p.getID(),
                         LogManager.DEBUG_MESSAGE_LEVEL);
            mPDAXWriter.write( p );
            mLogger.logCompletion( "Writing to the pdax file for partition " + p.getID(),
                                     LogManager.DEBUG_MESSAGE_LEVEL);
            //write out the DAX file
            mDAXWriter.writePartitionDax( p );

        }
        catch( IOException ioe ){
            //wrap and throw in Runtime Exception
            throw new RuntimeException( "Writer Callback for partition " + p.getID(),
                                        ioe );
        }

    }

    /**
     * Callback for when a partitioner determines the relations between partitions
     * that it has previously constructed.
     *
     * @param child    the id of a partition.
     * @param parents  the list of <code>String</code> objects that contain
     *                 the id's of the parents of the partition.
     *
     *
     * @throws RuntimeException in case of any error while writing out the DAX or
     *         the PDAX files.
     */
    public void cbParents( String child, List parents ) {
        mPDAXWriter.write( partitionRelation2XML( child, parents ) );
    }

    /**
     * Callback for the partitioner to signal that it is done with the processing.
     * This internally closes all the handles to the DAX and PDAX writers.
     *
     */
    public void cbDone(){
        //change internal state to signal
        //that we are done with partitioning.
        mPartitioningStarted = false;
        mPDAXWriter.close();
        mDAXWriter.close();
    }




    /**
     * Returns the name of the partition, that needs to be set while creating
     * the Partition object corresponding to each partition.
     *
     * @return the name of the partition.
     */
    protected String getPartitionName(){
        return mDAXWriter.getPartitionName();
    }

    /**
     * It returns the handle to the writer for writing out the pdax file
     * that contains the relations amongst the partitions and the jobs making
     * up the partitions.
     *
     * @param daxFile    the path to the DAX file that is being partitioned.
     * @param name  the name/label that is to be assigned to the pdax file.
     * @param directory  the directory where the partitioned daxes have to reside.
     *
     * @return handle to the writer of pdax file.
     */
    protected PDAXWriter getHandletoPDAXWriter( String daxFile,
                                                String name,
                                                String directory ){
        String pdaxPath;
        //get the name of dax file sans the path
        String daxName = new java.io.File( daxFile ).getName();
        //construct the basename of the pdax file
        pdaxPath = (daxName == null)?
                       "partition":
                       ((daxName.indexOf('.') > 0)?
                           daxName.substring(0,daxName.indexOf('.')):
                           daxName)
                         ;
        //now the complete path
        pdaxPath = directory + File.separator + pdaxPath + ".pdax";
        //System.out.println("Name is " + nameOfPDAX);

        return new PDAXWriter( name, pdaxPath );

    }

    /**
     * Returns the xml description of a relation between 2 partitions.
     *
     * @param childID   the ID of the child.
     * @param parentID  the ID of the parent.
     *
     * @return the XML description of child parent relation.
     */
    protected String partitionRelation2XML( String childID , String parentID ){
        StringBuffer sb = new StringBuffer();
        sb.append("\n\t<child ref=\"").append(childID).append("\">");
        sb.append("\n\t\t<parent ref=\"").append(parentID).append("\"/>");
        sb.append("\n\t</child>");
        return sb.toString();
    }

    /**
     * Returns the xml description of a relation between 2 partitions.
     *
     * @param childID   the ID of the child
     * @param parentIDs <code>List</code> of parent IDs.
     *
     * @return the XML description of child parent relations.
     */
    protected String partitionRelation2XML( String childID , List parentIDs ){
        StringBuffer sb = new StringBuffer();
        sb.append("\n\t<child ref=\"").append(childID).append("\">");
        for( Iterator it = parentIDs.iterator(); it.hasNext(); ){
            sb.append("\n\t\t<parent ref=\"").append(it.next()).append("\"/>");
        }
        sb.append("\n\t</child>");
        return sb.toString();
    }

    /**
     * Returns the xml description of a relation between 2 partitions.
     *
     * @param childID   the ID of the child
     * @param parentIDs <code>Set</code> of parent IDs.
     *
     * @return the XML description of child parent relations.
     */
    protected String partitionRelation2XML( String childID , Set parentIDs ){
       StringBuffer sb = new StringBuffer();
       sb.append("\n\t<child ref=\"").append(childID).append("\">");
       for ( Iterator it = parentIDs.iterator(); it.hasNext(); ){
           sb.append("\n\t\t<parent ref=\"").append(it.next()).append("\"/>");
       }
       sb.append("\n\t</child>");
       return sb.toString();
   }


}
