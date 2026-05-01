package com.mukulramesh.fpscompress.portal;

/**
 * Defines the operational mode of a PreFab face.
 *
 * <p>Each face of a PreFab block can be configured independently:
 * <ul>
 *   <li>DISABLED - Face is inactive, no resource transport</li>
 *   <li>PULL - Extract from adjacent Overworld block → Transport to CM Importer</li>
 *   <li>PUSH - Extract from CM Exporter → Insert to adjacent Overworld block</li>
 * </ul>
 */
public enum FaceMode {
    /**
     * Face is disabled - no resource transport occurs.
     */
    DISABLED,

    /**
     * Pull mode - Extract resources from adjacent Overworld block and send to CM Importer.
     * <p>
     * Flow: Overworld chest → PreFab face → CM Importer → Factory machines
     */
    PULL,

    /**
     * Push mode - Extract resources from CM Exporter and insert to adjacent Overworld block.
     * <p>
     * Flow: Factory machines → CM Exporter → PreFab face → Overworld chest
     */
    PUSH
}
