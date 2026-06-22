const BANK_CONFIGURATION_ROLES = ["ADMIN_TENANT", "ADMIN"];
const EMPLOYEE_MANAGEMENT_ROLES = ["ADMIN_TENANT", "ADMIN"];
const CLIENT_MANAGEMENT_ROLES = ["ADMIN_TENANT", "ADMIN", "FINANCE", "SUPPORT"];
const INTEGRATIONS_MANAGEMENT_ROLES = ["ADMIN_TENANT", "ADMIN"];

function hasRole(roles: string[] | null | undefined, allowed: string[]) {
  if (!roles?.length) {
    return false;
  }

  return roles.some((role) => allowed.includes(role));
}

export function hasPermission(
  permissions: string[] | null | undefined,
  needed: string,
) {
  if (!permissions?.length) {
    return false;
  }

  return permissions.includes(needed);
}

export function canManageBankConfigurations(roles?: string[] | null) {
  return hasRole(roles, BANK_CONFIGURATION_ROLES);
}

export function canManageEmployees(
  roles?: string[] | null,
  permissions?: string[] | null,
) {
  return (
    hasPermission(permissions, "employees.create") ||
    hasRole(roles, EMPLOYEE_MANAGEMENT_ROLES)
  );
}

export function canAccessClients(
  roles?: string[] | null,
  permissions?: string[] | null,
) {
  return (
    hasPermission(permissions, "customers.read") ||
    hasRole(roles, CLIENT_MANAGEMENT_ROLES)
  );
}

export function canManageIntegrations(
  roles?: string[] | null,
  permissions?: string[] | null,
) {
  return (
    hasPermission(permissions, "integrations.manage") ||
    hasRole(roles, INTEGRATIONS_MANAGEMENT_ROLES)
  );
}
