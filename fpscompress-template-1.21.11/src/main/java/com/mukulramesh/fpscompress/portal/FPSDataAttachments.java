package com.mukulramesh.fpscompress.portal;

import com.mukulramesh.fpscompress.FPSCompress;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Stub for data attachments (deprecated).
 * Kept to prevent crashes/hangs from old references.
 *
 * TODO: Remove this file - see TODO.md "Code Cleanup & Technical Debt" section
 * BLOCKED: Still registered in FPSCompress.java:209
 */
public final class FPSDataAttachments {

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private FPSDataAttachments() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Empty DeferredRegister (stub to prevent crashes).
     */
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, FPSCompress.MODID);

    // No actual attachments registered - this is just a stub
}
