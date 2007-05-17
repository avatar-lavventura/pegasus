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
package org.griphyn.cPlanner.toolkit;

import org.griphyn.cPlanner.parser.DaxParser;

import org.griphyn.cPlanner.parser.dax.Callback;
import org.griphyn.cPlanner.parser.dax.DAX2Graph;
import org.griphyn.cPlanner.parser.dax.DAX2LabelGraph;
import org.griphyn.cPlanner.parser.dax.DAXCallbackFactory;

import org.griphyn.cPlanner.partitioner.WriterCallback;
import org.griphyn.cPlanner.partitioner.Partitioner;
import org.griphyn.cPlanner.partitioner.PartitionerFactory;

import org.griphyn.cPlanner.partitioner.graph.GraphNode;

import org.griphyn.cPlanner.common.LogManager;
import org.griphyn.cPlanner.common.PegasusProperties;

import org.griphyn.common.util.FactoryException;

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

import java.io.File;
import java.util.Date;
import java.util.Map;


/**
 * The class ends up partitioning the dax into smaller daxes according to the
 * various algorithms/criteria, to be used for deferred planning.
 *
 *
 * @author Karan Vahi
 * @version $Revision$
 */

public class PartitionDAX extends Executable {

    /**
     * The name of the default partitioner that is loaded, if none is specified
     * by the user.
     */
    public static final String DEFAULT_PARTITIONER_TYPE =
                               PartitionerFactory.DEFAULT_PARTITIONING_CLASS;

    /**
     * The path to the dax file that is to be partitioned.
     */
    private String mDAXFile;

    /**
     * The directory in which the partition daxes are generated.
     */
    private String mDirectory;

    /**
     * The type of the partitioner to be used. Is the same as the name of the
     * implementing class.
     */
    private String mType;

    /**
     * The object holding all the properties pertaining to Pegasus.
     */
    private PegasusProperties mProps;

    /**
     * The default constructor.
     */
    public PartitionDAX() {
        mProps     = PegasusProperties.nonSingletonInstance();
        mDAXFile   = null;
        mDirectory = ".";
        mType      = DEFAULT_PARTITIONER_TYPE;
    }

    /**
     * The main function of the class, that is invoked by the jvm. It calls
     * the executeCommand function.
     *
     * @param args  array of arguments.
     */
    public static void main(String[] args){
        PartitionDAX pdax = new PartitionDAX();
        pdax.executeCommand(args);
    }

    /**
     * Executes the partition dax on the basis of the options given by the
     * user.
     *
     * @param args  the arguments array  populated by the user options.
     */
    public void executeCommand(String[] args) {
        int option = 0;
        LongOpt[] longOptions = generateValidOptions();
        Getopt g = new Getopt("PartitionDAX", args, "vhVD:d:t:", longOptions, false);
        boolean help = false;
        boolean version = false;

        //log the starting time
        double starttime = new Date().getTime();
        int level = 0;
        while ( (option = g.getopt()) != -1) {
            //System.out.println("Option tag " + option);
            switch (option) {
                case 'd': //dax
                    mDAXFile = g.getOptarg();
                    break;

                case 'D': //dir
                    mDirectory = g.getOptarg();
                    break;

                case 't': //type
                    mType = g.getOptarg();
                    break;

                case 'v': //verbose
                    //set the verbose level in the logger
                    level++;
                    break;

                case 'V': //version
                    version = true;
                    break;

                case 'h': //help
                    help = true;
                    break;

                default: //same as help
                    mLogger.log("Unrecognized Option " +
                                Integer.toString(option),
                                LogManager.FATAL_MESSAGE_LEVEL);
                    printShortVersion();
                    System.exit(1);
                    break;

            }
        }
        if ( level > 0 ) {
            //set the logging level only if -v was specified
            //else bank upon the the default logging level
            mLogger.setLevel( level );
        }

        if ( ( help && version ) || help ) {
            printLongVersion();
            System.exit( 0 );
        }
        else if ( version ) {
            //print the version message
            mLogger.log( getGVDSVersion(), LogManager.INFO_MESSAGE_LEVEL );
            System.exit( 0 );
        }
        //sanity check for the dax file
        if ( mDAXFile == null || mDAXFile.length() == 0 ) {
            mLogger.log( "The dax file that is to be partitioned not " +
                         "specified", LogManager.FATAL_MESSAGE_LEVEL );
            printShortVersion();
            System.exit(1);
        }

        //always try to make the directory
        //referred to by the directory
        File dir = new File( mDirectory );
        dir.mkdirs();

        //build up the partition graph
        String callbackClass = ( mType.equalsIgnoreCase("label") ) ?
                                "DAX2LabelGraph": //graph with labels populated
                                "DAX2Graph";


        //load the appropriate partitioner
        Callback callback       = null;
        Partitioner partitioner = null;
        String daxName          = null;
        int state = 0;
        try{
            callback = DAXCallbackFactory.loadInstance( mProps,
                                                        mDAXFile,
                                                        callbackClass );

            //set the appropriate key that is to be used for picking up the labels
            if( callback instanceof DAX2LabelGraph ){
                ((DAX2LabelGraph)callback).setLabelKey( mProps.getPartitionerLabelKey() );
            }

            state = 1;
            DaxParser d = new DaxParser( mDAXFile, mProps, callback );
            state = 2;
            //get the graph map
            Map graphMap = (Map) callback.getConstructedObject();
            //get the fake dummy root node
            GraphNode root = (GraphNode) graphMap.get( DAX2Graph.DUMMY_NODE_ID );
            daxName = ( (DAX2Graph) callback).getNameOfDAX();
            state = 3;
            partitioner = PartitionerFactory.loadInstance( mProps,
                                                           root,
                                                           graphMap,
                                                           mType );
        }
        catch ( FactoryException fe){
            mLogger.log( fe.convertException() , LogManager.FATAL_MESSAGE_LEVEL);
            System.exit( 2 );
        }
        catch( Exception e ){
            int errorStatus = 1;
            switch(state){
                case 0:
                    mLogger.log( "Unable to load the DAXCallback", e,
                                 LogManager.FATAL_MESSAGE_LEVEL );
                    errorStatus = 2;
                    break;

                case 1:
                    mLogger.log( "Error while parsing the DAX file",
                                 LogManager.FATAL_MESSAGE_LEVEL );
                    errorStatus = 1;
                    break;

                case 2:
                    mLogger.log( "Error while determining the root of the parsed DAX",
                                 e, LogManager.FATAL_MESSAGE_LEVEL );
                    errorStatus = 1;
                    break;

                case 3:
                    mLogger.log( "Unable to load the partitioner", e,
                                 LogManager.FATAL_MESSAGE_LEVEL );
                    errorStatus = 2;
                    break;

                default:
                    mLogger.log( "Unknown Error", e,
                                 LogManager.FATAL_MESSAGE_LEVEL );
                    errorStatus = 1;
                    break;
            }
            System.exit( errorStatus );
        }


        //load the writer callback that writes out
        //the partitioned daxes and PDAX
        WriterCallback cb = new WriterCallback();
        cb.initialize( mProps, mDAXFile, daxName, mDirectory );

        //start the partitioning of the graph
        partitioner.determinePartitions( cb );

        //log the end time and time execute
        double endtime = new Date().getTime();
        double execTime = (endtime - starttime)/1000;
        mLogger.log("Time taken to execute is " + execTime + " seconds",
                    LogManager.INFO_MESSAGE_LEVEL);

        System.exit(0);


    }

    /**
     * Generates the short version of the help on the stdout.
     */
    public void printShortVersion() {
        String text =
          "\n $Id$ " +
          "\n" + getGVDSVersion() +
          "\n Usage :partitiondax -d <dax file> [-D <dir for partitioned daxes>] " +
          "   -t <type of partitioning to be used> [-v] [-V] [-h]";

        mLogger.log(text,LogManager.ERROR_MESSAGE_LEVEL);

    }


    /**
     * Generated the long version of the help on the stdout.
     */
    public void printLongVersion() {
        String text =
          "\n " + getGVDSVersion() +
          "\n CPlanner/partitiondax - The tool that is used to partition the dax "  +
          "\n into smaller daxes for use in deferred planning." +
          "\n " +
          "\n Usage :partitiondax --dax <dax file> [--dir <dir for partitioned daxes>] " +
          "\n --type <type of partitioning to be used> [--verbose] [--version] "  +
          "\n [--help]" +
           "\n" +
           "\n Mandatory Options " +
           "\n -d|--dax fn    the dax file that has to be partitioned into smaller daxes." +
           "\n Other Options  " +
           "\n -t|--type type the partitioning technique that is to be used for partitioning." +
           "\n -D|--dir dir   the directory in which the partitioned daxes reside (defaults to " +
           "\n                current directory)"+
           "\n -v|--verbose   increases the verbosity of messages about what is going on." +
           "\n -V|--version   displays the version number of the Griphyn Virtual Data System." +
           "\n -h|--help      generates this help";

       System.out.println(text);

    }

    /**
     * Tt generates the LongOpt which contain the valid options that the command
     * will accept.
     *
     * @return array of <code>LongOpt</code> objects , corresponding to the valid
     * options
     */
    public LongOpt[] generateValidOptions() {
        LongOpt[] longopts = new LongOpt[6];

        longopts[0]   = new LongOpt("dir",LongOpt.REQUIRED_ARGUMENT,null,'D');
        longopts[1]   = new LongOpt("dax",LongOpt.REQUIRED_ARGUMENT,null,'d');
        longopts[2]   = new LongOpt("type",LongOpt.REQUIRED_ARGUMENT,null,'t');
        longopts[3]   = new LongOpt("verbose",LongOpt.NO_ARGUMENT,null,'v');
        longopts[4]   = new LongOpt("version",LongOpt.NO_ARGUMENT,null,'V');
        longopts[5]   = new LongOpt("help",LongOpt.NO_ARGUMENT,null,'h');
        return longopts;

    }

    /**
     * Loads all the properties that are needed by this class.
     */
    public void loadProperties(){

    }


}
