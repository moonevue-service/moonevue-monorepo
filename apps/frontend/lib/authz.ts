const BANK_CONFIGURATION_ROLES = ['ADMIN_TENANT', 'ADMIN'];

export function canManageBankConfigurations(roles?: string[] | null) {
  if (!roles?.length) {
    return false;
  }

  return roles.some((role) => BANK_CONFIGURATION_ROLES.includes(role));
}