package com.mukulramesh.fpscompress.datagen;

import com.mukulramesh.fpscompress.FPSCompress;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.world.item.Items;

import java.util.concurrent.CompletableFuture;

/**
 * Recipe data generator for FPSCompress mod.
 */
public class ModRecipeProvider extends RecipeProvider {

    public ModRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider);
    }

    @Override
    protected void buildRecipes(RecipeOutput output) {
        // Simulation Wrench recipe
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, FPSCompress.SIMULATION_WRENCH.get())
                .pattern(" C ")
                .pattern(" CC")
                .pattern("C  ")
                .define('C', Items.COPPER_INGOT)
                .unlockedBy("has_copper", has(Items.COPPER_INGOT))
                .save(output);

        // Importer recipe
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, FPSCompress.IMPORTER_ITEM.get())
                .pattern("ISI")
                .pattern("TCT")
                .pattern("ISI")
                .define('I', Items.IRON_INGOT)
                .define('S', Items.STONE)
                .define('C', Items.COPPER_INGOT)
                .define('T', Items.CHEST)
                .unlockedBy("has_iron", has(Items.IRON_INGOT))
                .save(output);

        // Exporter recipe
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, FPSCompress.EXPORTER_ITEM.get())
                .pattern("ITI")
                .pattern("SCS")
                .pattern("ITI")
                .define('I', Items.IRON_INGOT)
                .define('S', Items.STONE)
                .define('C', Items.COPPER_INGOT)
                .define('T', Items.CHEST)
                .unlockedBy("has_iron", has(Items.IRON_INGOT))
                .save(output);

        // PreFab Upgrade Template recipe
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, FPSCompress.TPS_CACHE_UPGRADE.get())
                .pattern("GCG")
                .pattern("CSC")
                .pattern("GCG")
                .define('G', Items.GOLD_INGOT)
                .define('C', Items.COPPER_INGOT)
                .define('S', Items.STONE)
                .unlockedBy("has_gold", has(Items.GOLD_INGOT))
                .save(output);

        // Importer to Exporter conversion
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, FPSCompress.EXPORTER_ITEM.get())
                .requires(FPSCompress.IMPORTER_ITEM.get())
                .unlockedBy("has_importer", has(FPSCompress.IMPORTER_ITEM.get()))
                .save(output, "fpscompress:importer_to_exporter");

        // Exporter to Importer conversion
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, FPSCompress.IMPORTER_ITEM.get())
                .requires(FPSCompress.EXPORTER_ITEM.get())
                .unlockedBy("has_exporter", has(FPSCompress.EXPORTER_ITEM.get()))
                .save(output, "fpscompress:exporter_to_importer");
    }
}
