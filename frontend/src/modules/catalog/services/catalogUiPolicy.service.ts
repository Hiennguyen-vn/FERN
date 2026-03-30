import type { Product, Recipe, RecipeVersion } from '../model/catalog.types'

/**
 * catalogUiPolicy — mirror của CatalogAuthorizer bên backend.
 * Quyết định UI behavior dựa trên state của entity,
 * KHÔNG quyết định security (backend luôn enforce).
 */
export const catalogUiPolicy = {
  // Products
  canEditProduct: (product: Product): boolean => {
    return product.status !== 'DISCONTINUED'
  },

  canActivateProduct: (product: Product): boolean => {
    return product.status === 'INACTIVE'
  },

  canDiscontinueProduct: (product: Product): boolean => {
    return product.status !== 'DISCONTINUED'
  },

  // Recipe versions
  canActivateRecipeVersion: (rv: RecipeVersion): boolean => {
    return rv.status === 'DRAFT'
  },

  canArchiveRecipeVersion: (rv: RecipeVersion): boolean => {
    return rv.status === 'ACTIVE'
  },

  canEditRecipeVersion: (rv: RecipeVersion): boolean => {
    return rv.status === 'DRAFT'
  },

  canAddNewVersion: (_recipe: Recipe, versions: RecipeVersion[]): boolean => {
    // Allow new version if no DRAFT exists (only one draft at a time)
    const hasDraft = versions.some((v) => v.status === 'DRAFT')
    return !hasDraft
  },
}
