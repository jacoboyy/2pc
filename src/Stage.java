/*
 * @file   Stage.java
 * @author Tengda Wang <tengdaw@andrew.cmu.edu>
 *
 * Enum for the three different stages in the two-phase-commit protocol
 *
 * PROPOSE -> first stage where servers explicitly ask users whether they can commit
 * COMMIT -> second stage where the server send commit decision to the users
 * END -> the above two stages are finished
 */
import java.io.Serializable;

public enum Stage implements Serializable { PROPOSE, COMMIT, END }