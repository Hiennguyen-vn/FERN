import { useState } from 'react';
import { Shield, Users, Key, Globe, ShieldAlert, Eye } from 'lucide-react';
import { cn } from '@/lib/utils';
import { IAMDashboard } from './IAMDashboard';
import { UserManagement, UserDetail, CreateUserForm, EditUserForm } from './UserManagement';
import { RoleManagement, PermissionManagement, ScopeAssignment, OverrideManagement, EffectiveAccessInspector } from './RolePermissionManagement';
import type { IAMUser } from '@/types/iam';

type IAMView = 'dashboard' | 'users' | 'user-detail' | 'create-user' | 'edit-user' | 'roles' | 'permissions' | 'scopes' | 'overrides' | 'effective-access';

const IAM_TABS: { key: IAMView; label: string; icon: React.ElementType }[] = [
  { key: 'dashboard', label: 'Dashboard', icon: Shield },
  { key: 'users', label: 'Users', icon: Users },
  { key: 'roles', label: 'Roles', icon: Key },
  { key: 'permissions', label: 'Permissions', icon: Shield },
  { key: 'scopes', label: 'Scopes', icon: Globe },
  { key: 'overrides', label: 'Overrides', icon: ShieldAlert },
  { key: 'effective-access', label: 'Effective Access', icon: Eye },
];

export function IAMModule() {
  const [view, setView] = useState<IAMView>('dashboard');
  const [selectedUser, setSelectedUser] = useState<IAMUser | null>(null);

  const activeTab = (['user-detail', 'create-user', 'edit-user'].includes(view)) ? 'users' : view;

  return (
    <div className="flex flex-col h-full animate-fade-in">
      <div className="border-b bg-card px-6 flex items-center gap-0 flex-shrink-0">
        {IAM_TABS.map(tab => (
          <button
            key={tab.key}
            onClick={() => { setView(tab.key); setSelectedUser(null); }}
            className={cn(
              'flex items-center gap-1.5 px-4 py-3 text-xs font-medium border-b-2 transition-colors',
              activeTab === tab.key
                ? 'border-primary text-primary'
                : 'border-transparent text-muted-foreground hover:text-foreground'
            )}
          >
            <tab.icon className="h-3.5 w-3.5" />
            {tab.label}
          </button>
        ))}
      </div>

      <div className="flex-1 overflow-y-auto">
        <div className="p-6 space-y-5">
          {view === 'dashboard' && <IAMDashboard onNavigate={setView} />}
          {view === 'users' && !selectedUser && <UserManagement onSelectUser={u => { setSelectedUser(u); setView('user-detail'); }} onCreateUser={() => setView('create-user')} />}
          {view === 'user-detail' && selectedUser && <UserDetail user={selectedUser} onBack={() => { setView('users'); setSelectedUser(null); }} onEdit={() => setView('edit-user')} />}
          {view === 'create-user' && <CreateUserForm onBack={() => setView('users')} />}
          {view === 'edit-user' && selectedUser && <EditUserForm user={selectedUser} onBack={() => setView('user-detail')} />}
          {view === 'roles' && <RoleManagement />}
          {view === 'permissions' && <PermissionManagement />}
          {view === 'scopes' && <ScopeAssignment />}
          {view === 'overrides' && <OverrideManagement />}
          {view === 'effective-access' && <EffectiveAccessInspector />}
        </div>
      </div>
    </div>
  );
}
