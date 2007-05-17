/**
 * This file or a portion of this file is licensed under the terms of
 * the Globus Toolkit Public License, found at $PEGASUS_HOME/GTPL or
 * http://www.globus.org/toolkit/download/license.html.
 * This notice must appear in redistributions of this file
 * with or without modification.
 *
 * Redistributions of this Software, with or without modification, must reproduce
 * the GTPL in:
 * (1) the Software, or
 * (2) the Documentation or
 * some other similar material which is provided with the Software (if any).
 *
 * Copyright 1999-2004
 * University of Chicago and The University of Southern California.
 * All rights reserved.
 */
package org.griphyn.cPlanner.classes;

import java.util.StringTokenizer;

/**
 * This is a data class that stores the globus version installed and to be used
 * on a particular pool for the gridftp server or the jobmanagers.
 *
 * @author Gaurang Mehta gmehta@isi.edu
 * @author Karan Vahi vahi@isi.edu
 *
 * @version $Revision$
 */
public class GlobusVersion {

    /**
     * The constant for the major version type.
     */
    public static final String MAJOR = "major";

    /**
     * The constant for the minor version type.
     */
    public static final String MINOR = "minor";

    /**
     * The constant for patche version type.
     */
    public static final String PATCH = "patch";

    /**
     * This variable defines the major version number.
     */
    private int mMajorVersion;

    /**
     * This variable defines the minor version number.
     */
    private int mMinorVersion;

    /**
     * This variable defines the patch version number.
     */
    private int mPatchVersion;

    /**
     * The default constructor.
     */
    public GlobusVersion(){
        mMajorVersion = 0;
        mMinorVersion = 0;
        mPatchVersion = 0;
    }

    /**
     * Overloaded constructor for the class;
     *
     * @param version  a . separated String denoting the version . e.g. 2.2.4
     */
    public GlobusVersion(String version) {

        StringTokenizer st = new StringTokenizer(version, ".");
        int count = st.countTokens();

        mMajorVersion = (count > 0) ? new Integer(st.nextToken()).intValue():0;
        mMinorVersion = (count > 1) ? new Integer(st.nextToken()).intValue():0;
        mPatchVersion = (count > 2) ? new Integer(st.nextToken()).intValue():0;

    }

    /**
     * Constructor to set the version information
     *
     * @param major Specifies the Major version number.
     * @param minor Specifies the minor version number.
     * @param patch Specifies the patch version number.
     */
    public GlobusVersion(int major, int minor, int patch) {
        mMajorVersion = major;
        mMinorVersion = minor;
        mPatchVersion = patch;
    }


    /**
     * Returns the version corresponding to a particular version type.
     * If an invalid version type is specified then 0 is returned.
     *
     * @param version the <code>String</code> type corresponding to the version that
     *             you want.
     *
     * @return int value corresponding to the version,
     *         0 in case of incorrect version type.
     *
     * @see #MAJOR
     * @see #MINOR
     * @see #PATCH
     */
    public int getGlobusVersion(String version) {
        int value = 0;
        if (version.equalsIgnoreCase(MAJOR)) {
            value = mMajorVersion;
        }
        else if (version.equalsIgnoreCase(MINOR)) {
            value = mMinorVersion;
        }
        else if (version.equalsIgnoreCase(PATCH)) {
            value = mPatchVersion;
        }
        return value;
    }

    /**
     * Returns the Globus version as a dot separated String.
     * It is of type major.minor.patch where major, minor and patch are the
     * various version numbers stored in the class.
     *
     * @return the version a dot separated String.
     */
    public String getGlobusVersion(){
        StringBuffer version = new StringBuffer(5);
        version.append(mMajorVersion).append(".")
               .append(mMinorVersion).append(".").
                append(mPatchVersion);

        return version.toString();
    }

    /**
     * Returns the textual description of the  contents of <code>GlobusVersion</code>
     * object in the multiline format.
     *
     * @return the textual description in multiline format.
     */
    public String toMultiLine() {
        return getGlobusVersion();
    }

    /**
     * Returns the textual description of the  contents of <code>GlobusVersion
     * </code> object.
     *
     * @return the textual description.
     */
    public String toString() {
        return getGlobusVersion();
    }

}
