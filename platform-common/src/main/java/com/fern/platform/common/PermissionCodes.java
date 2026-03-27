package com.fern.platform.common;

public final class PermissionCodes {
    public static final String IAM_USER_READ = "iam.user.read";
    public static final String IAM_USER_WRITE = "iam.user.write";
    public static final String IAM_ROLE_READ = "iam.role.read";
    public static final String IAM_ROLE_WRITE = "iam.role.write";
    public static final String IAM_ROLE_ASSIGN = "iam.role.assign";
    public static final String IAM_SCOPE_ASSIGN = "iam.scope.assign";
    public static final String IAM_PERMISSION_READ = "iam.permission.read";
    public static final String IAM_PERMISSION_OVERRIDE_READ = "iam.permission_override.read";
    public static final String IAM_PERMISSION_OVERRIDE_WRITE = "iam.permission_override.write";
    public static final String ORG_REGION_READ = "org.region.read";
    public static final String ORG_REGION_WRITE = "org.region.write";
    public static final String ORG_OUTLET_READ = "org.outlet.read";
    public static final String ORG_OUTLET_WRITE = "org.outlet.write";
    public static final String ORG_SCOPE_RESOLVE = "org.scope.resolve";
    public static final String AUDIT_READ = "audit.read";
    public static final String AUDIT_DETAIL_READ = "audit.detail.read";
    public static final String AUDIT_EXPORT = "audit.export";
    public static final String CATALOG_INGREDIENT_READ = "catalog.ingredient.read";
    public static final String CATALOG_INGREDIENT_WRITE = "catalog.ingredient.write";
    public static final String CATALOG_PRODUCT_READ = "catalog.product.read";
    public static final String CATALOG_PRODUCT_WRITE = "catalog.product.write";
    public static final String CATALOG_RECIPE_READ = "catalog.recipe.read";
    public static final String CATALOG_RECIPE_WRITE = "catalog.recipe.write";
    public static final String CATALOG_PRICE_READ = "catalog.price.read";
    public static final String CATALOG_PRICE_WRITE = "catalog.price.write";
    public static final String CATALOG_INTERNAL_RESOLVE = "catalog.internal.resolve";

    private PermissionCodes() {
    }
}
