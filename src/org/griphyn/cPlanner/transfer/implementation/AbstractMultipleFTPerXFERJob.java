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

package org.griphyn.cPlanner.transfer.implementation;

import org.griphyn.cPlanner.classes.SubInfo;
import org.griphyn.cPlanner.classes.TransferJob;
import org.griphyn.cPlanner.classes.PlannerOptions;
import org.griphyn.cPlanner.classes.SiteInfo;
import org.griphyn.cPlanner.classes.JobManager;

import org.griphyn.cPlanner.common.LogManager;
import org.griphyn.cPlanner.common.PegasusProperties;

import org.griphyn.cPlanner.transfer.MultipleFTPerXFERJob;

import org.griphyn.common.catalog.TransformationCatalogEntry;

import java.io.File;
import java.io.FileWriter;

import java.util.Collection;
import java.util.HashSet;


/**
 * An abstract implementation for implementations that can handle multiple
 * file transfers in a single file transfer job.
 *
 * @author Karan Vahi
 * @version $Revision$
 */

public abstract class AbstractMultipleFTPerXFERJob extends Abstract
                      implements MultipleFTPerXFERJob  {



    /**
     * The overloaded constructor, that is called by the Factory to load the
     * class.
     *
     * @param properties  the properties object.
     * @param options     the options passed to the Planner.
     */
    public AbstractMultipleFTPerXFERJob(PegasusProperties properties,
                                    PlannerOptions options) {
        super(properties, options);
    }

    /**
     * Constructs a general transfer job that handles multiple transfers per
     * transfer job. There are appropriate callouts to generate the implementation
     * specific details.
     *
     * @param job         the SubInfo object for the job, in relation to which
     *                    the transfer node is being added. Either the transfer
     *                    node can be transferring this jobs input files to
     *                    the execution pool, or transferring this job's output
     *                    files to the output pool.
     * @param files       collection of <code>FileTransfer</code> objects
     *                    representing the data files and staged executables to be
     *                    transferred.
     * @param execFiles   subset collection of the files parameter, that identifies
     *                    the executable files that are being transferred.
     * @param txJobName   the name of transfer node.
     * @param jobClass    the job Class for the newly added job. Can be one of the
     *                    following:
     *                              stage-in
     *                              stage-out
     *                              inter-pool transfer
     *
     * @return  the created TransferJob.
     */
    public TransferJob createTransferJob(SubInfo job,
                                         Collection files,
                                         Collection execFiles,
                                         String txJobName,
                                         int jobClass) {
        TransferJob txJob = new TransferJob();
        SiteInfo ePool;
        JobManager jobmanager;

        //site where the transfer is scheduled
        //to be run. For thirdparty site it makes
        //sense to schedule on the local host unless
        //explicitly designated to run TPT on remote site
        String tPool = mRefiner.isSiteThirdParty(job.getSiteHandle(),jobClass) ?
                                //check if third party have to be run on remote site
                                mRefiner.runTPTOnRemoteSite(job.getSiteHandle(),jobClass) ?
                                          job.getSiteHandle() : "local"
                                :job.getSiteHandle();

        //the non third party site for the transfer job is
        //always the job execution site for which the transfer
        //job is being created.
        txJob.setNonThirdPartySite(job.getSiteHandle());


        //we first check if there entry for transfer universe,
        //if no then go for globus
        ePool = mSCHandle.getTXPoolEntry(tPool);

        txJob.jobName = txJobName;
        txJob.executionPool = tPool;
        txJob.condorUniverse = "globus";

        TransformationCatalogEntry tcEntry = this.getTransformationCatalogEntry(tPool);
        if(tcEntry == null){
            //should throw a TC specific exception
            StringBuffer error = new StringBuffer();
            error.append( "Could not find entry in tc for lfn " ).append( getCompleteTCName() ).
                  append(" at site " ).append( txJob.getSiteHandle());
            mLogger.log( error.toString(), LogManager.ERROR_MESSAGE_LEVEL);
            throw new RuntimeException( error.toString() );
        }


        txJob.namespace   = tcEntry.getLogicalNamespace();
        txJob.logicalName = tcEntry.getLogicalName();
        txJob.version     = tcEntry.getLogicalVersion();

        txJob.dvName      = this.getDerivationName();
        txJob.dvNamespace = this.getDerivationNamespace();
        txJob.dvVersion   = this.getDerivationVersion();

        //this should in fact only be set
        // for non third party pools
        jobmanager = ePool.selectJobManager(this.TRANSFER_UNIVERSE,true);
        txJob.globusScheduler = (jobmanager == null) ?
                                  null :
                                  jobmanager.getInfo(JobManager.URL);

        txJob.jobClass = jobClass;
        txJob.jobID = job.jobName;

        txJob.stdErr = "";
        txJob.stdOut = "";

        txJob.executable = tcEntry.getPhysicalTransformation();

        //the i/p and o/p files remain empty
        //as we doing just copying urls
        txJob.inputFiles = new HashSet();

        //to get the file stat information we need to put
        //the files as output files of the transfer job
        txJob.outputFiles = new HashSet( files );

        try{
            txJob.stdIn = prepareSTDIN(txJobName, files);
        } catch (Exception e) {
            mLogger.log("Unable to write the stdIn file for job " +
                        txJob.getCompleteTCName() + " " + e.getMessage(),
                        LogManager.ERROR_MESSAGE_LEVEL);
        }

        //the profile information from the pool catalog needs to be
        //assimilated into the job.
        txJob.updateProfiles(mSCHandle.getPoolProfile(tPool));

        //the profile information from the transformation
        //catalog needs to be assimilated into the job
        //overriding the one from pool catalog.
        txJob.updateProfiles(tcEntry);

        //the profile information from the properties file
        //is assimilated overidding the one from transformation
        //catalog.
        txJob.updateProfiles(mProps);

        //take care of transfer of proxies
        this.checkAndTransferProxy(txJob);

        //apply the priority to the transfer job
        this.applyPriority(txJob);

        //constructing the arguments to transfer script
        //they only have to be incorporated after the
        //profile incorporation
        txJob.strargs = this.generateArgumentString(txJob);

        if(execFiles != null){
            //we need to add setup jobs to change the XBit
            super.addSetXBitJobs(job,txJob,execFiles);
        }

        //a callout that allows the derived transfer implementation
        //classes do their own specific stuff on the job
        this.postProcess( txJob );

        return txJob;
    }

    /**
     * An optional method that allows the derived classes to do their own
     * post processing on the the transfer job before it is returned to
     * the calling module.
     *
     * @job  the <code>TransferJob</code> that has been created.
     */
    public void postProcess( TransferJob job ){

    }

    /**
     * Prepares the stdin for the transfer job. Usually involves writing out a
     * text file that Condor transfers to the remote end.
     *
     * @param name  the name of the transfer job.
     * @param files    Collection of <code>FileTransfer</code> objects containing
     *                 the information about sourceam fin and destURL's.
     *
     * @return  the path to the prepared stdin file.
     *
     * @throws Exception in case of error.
     */
    protected String prepareSTDIN(String name, Collection files)throws Exception{
        //writing the stdin file
        FileWriter stdIn;
        String basename = name + ".in";
        stdIn = new FileWriter(new File(mPOptions.getSubmitDirectory(),
                                        basename));
        writeJumboStdIn(stdIn, files);
        //close the stdin stream
        stdIn.close();
        return basename;
    }


    /**
     * Returns the namespace of the derivation that this implementation
     * refers to.
     *
     * @return the namespace of the derivation.
     */
    protected abstract String getDerivationNamespace();


    /**
     * Returns the logical name of the derivation that this implementation
     * refers to.
     *
     * @return the name of the derivation.
     */
    protected abstract String getDerivationName();

    /**
     * Returns the version of the derivation that this implementation
     * refers to.
     *
     * @return the version of the derivation.
     */
    protected abstract String getDerivationVersion();

    /**
     * It constructs the arguments to the transfer executable that need to be passed
     * to the executable referred to in this transfer mode.
     *
     * @param job   the object containing the transfer node.
     * @return  the argument string
     */
    protected abstract String generateArgumentString(TransferJob job);

    /**
     * Writes to a FileWriter stream the stdin which goes into the magic script
     * via standard input
     *
     * @param stdIn    the writer to the stdin file.
     * @param files    Collection of <code>FileTransfer</code> objects containing
     *                 the information about sourceam fin and destURL's.
     *
     * @throws Exception
     */
    protected abstract void writeJumboStdIn(FileWriter stdIn, Collection files)
              throws Exception ;

    /**
     * Returns the complete name for the transformation that the implementation
     * is using..
     *
     * @return the complete name.
     */
    protected abstract String getCompleteTCName();


}
