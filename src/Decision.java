/*
 * @file   Decision.java
 * @author Tengda Wang <tengdaw@andrew.cmu.edu>
 *
 * Enum class for the decision of the two-phase-commit protocol.
 * COMMIT -> all participants vote YES
 * ABORT -> at least one participants votes NO
 * UNKNOWN -> decision are not known yet, used during initilization on the user side
 */

import java.io.Serializable;

public enum Decision implements Serializable { COMMIT, ABORT, UNKNOWN }