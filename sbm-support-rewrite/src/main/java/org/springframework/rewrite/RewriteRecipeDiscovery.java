package org.springframework.rewrite;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.openrewrite.Recipe;
import org.openrewrite.Validated;
import org.openrewrite.Validated.Invalid;
import org.openrewrite.config.ClasspathScanningLoader;
import org.openrewrite.config.Environment;
import org.openrewrite.config.RecipeDescriptor;
import org.openrewrite.config.ResourceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.rewrite.parser.RecipeValidationErrorException;
import org.springframework.rewrite.parser.SpringRewriteProperties;

public class RewriteRecipeDiscovery {
   private static final Logger LOGGER = LoggerFactory.getLogger(RewriteRecipeDiscovery.class);
   private final SpringRewriteProperties springRewriteProperties;

   public RewriteRecipeDiscovery(SpringRewriteProperties springRewriteProperties) {
      this.springRewriteProperties = springRewriteProperties;
   }

   public List<Recipe> discoverRecipes() {
      ClasspathScanningLoader resourceLoader = new ClasspathScanningLoader(new Properties(), new String[0]);
      return Environment.builder().load(resourceLoader).build().listRecipes();
   }

   public List<Recipe> discoverFilteredRecipes(
      List<String> activeRecipes, Properties properties, String[] acceptPackages, ClasspathScanningLoader classpathScanningLoader
   ) {
      if (activeRecipes.isEmpty()) {
         LOGGER.warn("No active recipes were provided.");
         return Collections.emptyList();
      } else {
         List<Recipe> recipes = new ArrayList<>();
         Environment environment = Environment.builder(properties).load(classpathScanningLoader).build();
         Recipe recipe = environment.activateRecipes(activeRecipes);
         if (recipe.getRecipeList().isEmpty()) {
            LOGGER.warn("No recipes were activated. None of the provided 'activeRecipes' matched any of the applicable recipes.");
            return Collections.emptyList();
         } else {
            Collection<Validated<Object>> validated = recipe.validateAll();
            List<Invalid<Object>> failedValidations = validated.stream().map(Validated::failures).flatMap(Collection::stream).collect(Collectors.toList());
            if (!failedValidations.isEmpty()) {
               failedValidations.forEach(
                  failedValidation -> LOGGER.error(
                        "Recipe validation error in " + failedValidation.getProperty() + ": " + failedValidation.getMessage(), failedValidation.getException()
                     )
               );
               if (this.springRewriteProperties.isFailOnInvalidActiveRecipes()) {
                  throw new RecipeValidationErrorException("Recipe validation errors detected as part of one or more activeRecipe(s). Please check error logs.");
               }

               LOGGER.error("Recipe validation errors detected as part of one or more activeRecipe(s). Execution will continue regardless.");
            }

            recipes.add(recipe);
            return recipes;
         }
      }
   }

   public RecipeDescriptor findRecipeDescriptor(String anotherDummyRecipe) {
      ResourceLoader resourceLoader = new ClasspathScanningLoader(new Properties(), new String[]{"io.example"});
      Environment environment = Environment.builder().load(resourceLoader).build();
      Collection<RecipeDescriptor> recipeDescriptors = environment.listRecipeDescriptors();
      return recipeDescriptors.stream().filter(rd -> "AnotherDummyRecipe".equals(rd.getDisplayName())).findFirst().get();
   }

   public List<Recipe> findRecipesByTag(String tag) {
      return getFilteredRecipes(r -> r.getTags().contains(tag));
   }

   public Optional<Recipe> findRecipeByName(String name) {
      List<Recipe> filteredRecipes = getFilteredRecipes(r -> r.getName().equals(name));
      if (filteredRecipes.size() > 1) {
         throw new IllegalStateException("Found more than one recipe with name '%s'".formatted(name));
      } else {
         return filteredRecipes.isEmpty() ? Optional.empty() : Optional.of(filteredRecipes.get(0));
      }
   }

   public Recipe getRecipeByName(String name) {
      List<Recipe> filteredRecipes = getFilteredRecipes(r -> r.getName().equals(name));
      if (filteredRecipes.size() > 1) {
         throw new IllegalArgumentException("Found more than one recipe with name '%s'".formatted(name));
      } else if (filteredRecipes.isEmpty()) {
         throw new IllegalArgumentException("No recipe found with name '%s'".formatted(name));
      } else {
         return filteredRecipes.get(0);
      }
   }

   @NotNull
   public static List<Recipe> getFilteredRecipes(Predicate<Recipe> filterPredicate) {
      ResourceLoader resourceLoader = new ClasspathScanningLoader(new Properties(), new String[0]);
      Environment environment = Environment.builder().load(resourceLoader).build();
      return environment.listRecipes().stream().filter(filterPredicate).toList();
   }
}
