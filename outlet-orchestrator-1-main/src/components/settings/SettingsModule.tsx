import { useState, useEffect } from 'react';
import {
  Building2, MapPin, Globe, Palette, Settings, Plus, Edit2, Trash2,
  ToggleLeft, ToggleRight, ChevronRight, Loader2, X,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog';
import { supabase } from '@/integrations/supabase/client';
import { toast } from 'sonner';

type SettingsTab = 'general' | 'outlets' | 'regions' | 'branding' | 'preferences';

const TABS: { key: SettingsTab; label: string; icon: React.ElementType }[] = [
  { key: 'general', label: 'General', icon: Settings },
  { key: 'outlets', label: 'Outlets', icon: Building2 },
  { key: 'regions', label: 'Regions', icon: MapPin },
  { key: 'branding', label: 'Branding', icon: Palette },
  { key: 'preferences', label: 'Preferences', icon: Globe },
];

interface Outlet {
  id: string;
  name: string;
  region: string | null;
  address: string | null;
  status: string;
  created_at: string;
}

const PREFERENCES = [
  { key: 'timezone', label: 'Default Timezone', value: 'Asia/Singapore (UTC+8)', type: 'select' },
  { key: 'currency', label: 'Default Currency', value: 'USD — US Dollar', type: 'select' },
  { key: 'date_format', label: 'Date Format', value: 'YYYY-MM-DD', type: 'select' },
  { key: 'language', label: 'Language', value: 'English (US)', type: 'select' },
  { key: 'session_timeout', label: 'Session Timeout', value: '30 minutes', type: 'select' },
  { key: 'auto_logout', label: 'Auto Logout on Idle', value: true, type: 'toggle' },
  { key: 'email_notifications', label: 'Email Notifications', value: true, type: 'toggle' },
  { key: 'two_factor', label: 'Require 2FA for Managers', value: false, type: 'toggle' },
];

function StatusBadge({ status }: { status: string }) {
  const s: Record<string, string> = {
    active: 'bg-success/10 text-success',
    maintenance: 'bg-warning/10 text-warning',
    inactive: 'bg-muted text-muted-foreground',
  };
  return <span className={`text-[10px] px-2 py-0.5 rounded-full font-medium ${s[status] || s.active}`}>{status}</span>;
}

/* ── General ── */
function GeneralSettings() {
  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-lg font-semibold text-foreground">General Settings</h2>
        <p className="text-xs text-muted-foreground mt-0.5">Organization-wide configuration</p>
      </div>
      <div className="surface-elevated p-5 space-y-4">
        <div className="flex items-center gap-4">
          <div className="h-16 w-16 rounded-xl bg-primary/10 flex items-center justify-center">
            <span className="text-2xl font-bold text-primary">O</span>
          </div>
          <div className="flex-1">
            <h3 className="text-sm font-semibold text-foreground">OpsCenter Enterprise</h3>
            <p className="text-xs text-muted-foreground mt-0.5">Multi-outlet F&B operations platform</p>
          </div>
          <Button size="sm" variant="outline" className="h-8 text-xs gap-1.5"><Edit2 className="h-3 w-3" /> Edit</Button>
        </div>
      </div>
      <div className="surface-elevated overflow-hidden">
        <div className="px-4 py-3 border-b">
          <span className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wide">Organization Details</span>
        </div>
        <div className="p-4 space-y-1">
          {[
            { label: 'Organization Name', value: 'OpsCenter Enterprise' },
            { label: 'Legal Name', value: 'OpsCenter Holdings Pte Ltd' },
            { label: 'Registration No.', value: 'UEN-202612345A' },
            { label: 'Tax ID', value: 'GST-2026-7890' },
            { label: 'Primary Contact', value: 'admin@opscenter.io' },
            { label: 'Address', value: '100 Innovation Drive, Singapore 138000' },
          ].map(item => (
            <div key={item.label} className="flex items-center justify-between py-2.5 px-3 rounded-lg hover:bg-muted/20 transition-colors">
              <span className="text-sm text-muted-foreground">{item.label}</span>
              <span className="text-sm font-medium text-foreground">{item.value}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

/* ── Outlet CRUD ── */
function OutletSettings() {
  const [outlets, setOutlets] = useState<Outlet[]>([]);
  const [loading, setLoading] = useState(true);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [editing, setEditing] = useState<Outlet | null>(null);
  const [form, setForm] = useState({ name: '', region: '', address: '', status: 'active' });

  const fetchOutlets = async () => {
    setLoading(true);
    const { data } = await supabase.from('outlets').select('*').order('name');
    setOutlets(data || []);
    setLoading(false);
  };

  useEffect(() => { fetchOutlets(); }, []);

  const regions = [...new Set(outlets.map(o => o.region).filter(Boolean))] as string[];

  const openCreate = () => {
    setEditing(null);
    setForm({ name: '', region: '', address: '', status: 'active' });
    setDialogOpen(true);
  };

  const openEdit = (o: Outlet) => {
    setEditing(o);
    setForm({ name: o.name, region: o.region || '', address: o.address || '', status: o.status });
    setDialogOpen(true);
  };

  const handleSave = async () => {
    if (!form.name.trim()) { toast.error('Name is required'); return; }
    if (editing) {
      const { error } = await supabase.from('outlets').update({
        name: form.name, region: form.region || null, address: form.address || null, status: form.status,
      }).eq('id', editing.id);
      if (error) { toast.error(error.message); return; }
      toast.success('Outlet updated');
    } else {
      const { error } = await supabase.from('outlets').insert({
        name: form.name, region: form.region || null, address: form.address || null, status: form.status,
      });
      if (error) { toast.error(error.message); return; }
      toast.success('Outlet created');
    }
    setDialogOpen(false);
    fetchOutlets();
  };

  const handleDelete = async () => {
    if (!editing) return;
    const { error } = await supabase.from('outlets').delete().eq('id', editing.id);
    if (error) { toast.error(error.message); return; }
    toast.success('Outlet deleted');
    setDeleteOpen(false);
    setEditing(null);
    fetchOutlets();
  };

  return (
    <div className="space-y-5">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-lg font-semibold text-foreground">Outlet Management</h2>
          <p className="text-xs text-muted-foreground mt-0.5">
            {outlets.length} outlets across {regions.length} regions
          </p>
        </div>
        <Button size="sm" className="h-8 text-xs gap-1.5" onClick={openCreate}><Plus className="h-3 w-3" /> Add Outlet</Button>
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-12"><Loader2 className="h-5 w-5 animate-spin text-muted-foreground" /></div>
      ) : (
        <div className="surface-elevated overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b bg-muted/30">
                {['Outlet', 'Region', 'Address', 'Status', ''].map(h => (
                  <th key={h} className="text-[11px] font-medium text-muted-foreground px-4 py-2.5 text-left">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {outlets.map(o => (
                <tr key={o.id} className="border-b last:border-0 hover:bg-muted/20 transition-colors cursor-pointer" onClick={() => openEdit(o)}>
                  <td className="px-4 py-2.5 text-sm font-medium text-foreground">{o.name}</td>
                  <td className="px-4 py-2.5 text-xs text-muted-foreground">{o.region || '—'}</td>
                  <td className="px-4 py-2.5 text-xs text-muted-foreground">{o.address || '—'}</td>
                  <td className="px-4 py-2.5"><StatusBadge status={o.status} /></td>
                  <td className="px-4 py-2.5"><ChevronRight className="h-3.5 w-3.5 text-muted-foreground" /></td>
                </tr>
              ))}
              {outlets.length === 0 && (
                <tr><td colSpan={5} className="px-4 py-8 text-center text-sm text-muted-foreground">No outlets yet. Create one to get started.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {/* Create / Edit Dialog */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{editing ? 'Edit Outlet' : 'Create Outlet'}</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div>
              <Label className="text-xs">Name *</Label>
              <Input value={form.name} onChange={e => setForm(p => ({ ...p, name: e.target.value }))} className="mt-1" placeholder="e.g. Downtown Flagship" />
            </div>
            <div>
              <Label className="text-xs">Region</Label>
              <Input value={form.region} onChange={e => setForm(p => ({ ...p, region: e.target.value }))} className="mt-1" placeholder="e.g. Central Region" />
            </div>
            <div>
              <Label className="text-xs">Address</Label>
              <Input value={form.address} onChange={e => setForm(p => ({ ...p, address: e.target.value }))} className="mt-1" placeholder="e.g. 123 Main St" />
            </div>
            <div>
              <Label className="text-xs">Status</Label>
              <select
                value={form.status}
                onChange={e => setForm(p => ({ ...p, status: e.target.value }))}
                className="mt-1 w-full h-10 rounded-md border border-input bg-background px-3 text-sm"
              >
                <option value="active">Active</option>
                <option value="maintenance">Maintenance</option>
                <option value="inactive">Inactive</option>
              </select>
            </div>
          </div>
          <DialogFooter className="flex justify-between gap-2">
            {editing && (
              <Button variant="destructive" size="sm" className="mr-auto h-8 text-xs gap-1" onClick={() => { setDialogOpen(false); setDeleteOpen(true); }}>
                <Trash2 className="h-3 w-3" /> Delete
              </Button>
            )}
            <div className="flex gap-2">
              <Button variant="outline" size="sm" className="h-8 text-xs" onClick={() => setDialogOpen(false)}>Cancel</Button>
              <Button size="sm" className="h-8 text-xs" onClick={handleSave}>{editing ? 'Save Changes' : 'Create'}</Button>
            </div>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Delete Confirm */}
      <Dialog open={deleteOpen} onOpenChange={setDeleteOpen}>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader>
            <DialogTitle>Delete Outlet</DialogTitle>
          </DialogHeader>
          <p className="text-sm text-muted-foreground">Are you sure you want to delete <strong>{editing?.name}</strong>? This action cannot be undone.</p>
          <DialogFooter>
            <Button variant="outline" size="sm" className="h-8 text-xs" onClick={() => setDeleteOpen(false)}>Cancel</Button>
            <Button variant="destructive" size="sm" className="h-8 text-xs" onClick={handleDelete}>Delete</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

/* ── Regions ── */
function RegionSettings() {
  const [outlets, setOutlets] = useState<Outlet[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    supabase.from('outlets').select('*').order('region').then(({ data }) => {
      setOutlets(data || []);
      setLoading(false);
    });
  }, []);

  const regionMap = outlets.reduce<Record<string, Outlet[]>>((acc, o) => {
    const r = o.region || 'Unassigned';
    (acc[r] = acc[r] || []).push(o);
    return acc;
  }, {});
  const regions = Object.entries(regionMap);

  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-lg font-semibold text-foreground">Region Management</h2>
        <p className="text-xs text-muted-foreground mt-0.5">Regions are derived from outlet assignments</p>
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-12"><Loader2 className="h-5 w-5 animate-spin text-muted-foreground" /></div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {regions.map(([name, outs]) => (
            <div key={name} className="surface-elevated p-5 hover:shadow-md transition-shadow">
              <div className="flex items-center justify-between mb-3">
                <div className="h-9 w-9 rounded-lg bg-primary/10 flex items-center justify-center">
                  <MapPin className="h-4 w-4 text-primary" />
                </div>
                <StatusBadge status="active" />
              </div>
              <h3 className="text-sm font-semibold text-foreground">{name}</h3>
              <div className="mt-3 pt-3 border-t space-y-1">
                {outs.map(o => (
                  <div key={o.id} className="flex items-center justify-between text-xs">
                    <span className="text-foreground">{o.name}</span>
                    <StatusBadge status={o.status} />
                  </div>
                ))}
              </div>
              <p className="text-[10px] text-muted-foreground mt-2">{outs.length} outlet{outs.length !== 1 ? 's' : ''}</p>
            </div>
          ))}
          {regions.length === 0 && (
            <p className="col-span-3 text-center text-sm text-muted-foreground py-8">No regions. Assign regions to outlets in the Outlets tab.</p>
          )}
        </div>
      )}
    </div>
  );
}

/* ── Branding ── */
function BrandingSettings() {
  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-lg font-semibold text-foreground">Branding</h2>
        <p className="text-xs text-muted-foreground mt-0.5">Customize the look and feel of your platform</p>
      </div>
      <div className="surface-elevated p-5 space-y-5">
        <div>
          <label className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wide">Logo</label>
          <div className="mt-2 flex items-center gap-4">
            <div className="h-16 w-16 rounded-xl bg-primary flex items-center justify-center">
              <span className="text-primary-foreground font-bold text-xl">O</span>
            </div>
            <Button size="sm" variant="outline" className="h-8 text-xs">Upload Logo</Button>
          </div>
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wide">Primary Color</label>
            <div className="mt-2 flex items-center gap-2">
              <div className="h-8 w-8 rounded-md bg-primary border" />
              <Input value="hsl(217, 91%, 60%)" readOnly className="h-8 text-xs font-mono" />
            </div>
          </div>
          <div>
            <label className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wide">Accent Color</label>
            <div className="mt-2 flex items-center gap-2">
              <div className="h-8 w-8 rounded-md bg-accent border" />
              <Input value="hsl(210, 40%, 96%)" readOnly className="h-8 text-xs font-mono" />
            </div>
          </div>
        </div>
        <div>
          <label className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wide">Platform Name</label>
          <Input value="OpsCenter" className="mt-2 h-9 text-sm" readOnly />
        </div>
      </div>
    </div>
  );
}

/* ── Preferences ── */
function PreferencesSettings() {
  const [prefs, setPrefs] = useState(PREFERENCES);
  const togglePref = (key: string) => {
    setPrefs(prev => prev.map(p => p.key === key ? { ...p, value: !p.value } : p));
  };

  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-lg font-semibold text-foreground">System Preferences</h2>
        <p className="text-xs text-muted-foreground mt-0.5">Default settings applied across all outlets</p>
      </div>
      <div className="surface-elevated overflow-hidden">
        <div className="px-4 py-3 border-b">
          <span className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wide">Defaults</span>
        </div>
        <div className="p-4 space-y-1">
          {prefs.map(p => (
            <div key={p.key} className="flex items-center justify-between py-2.5 px-3 rounded-lg hover:bg-muted/20 transition-colors">
              <div>
                <p className="text-sm font-medium text-foreground">{p.label}</p>
                <p className="text-[10px] font-mono text-muted-foreground mt-0.5">{p.key}</p>
              </div>
              {p.type === 'toggle' ? (
                <button onClick={() => togglePref(p.key)} className="text-primary">
                  {p.value ? <ToggleRight className="h-6 w-6" /> : <ToggleLeft className="h-6 w-6 text-muted-foreground" />}
                </button>
              ) : (
                <span className="text-xs text-foreground bg-muted px-2 py-0.5 rounded">{String(p.value)}</span>
              )}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

/* ── Main ── */
export function SettingsModule() {
  const [tab, setTab] = useState<SettingsTab>('general');

  return (
    <div className="flex flex-col h-full animate-fade-in">
      <div className="border-b bg-card px-6 flex items-center gap-0 flex-shrink-0">
        {TABS.map(t => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            className={cn(
              'flex items-center gap-1.5 px-4 py-3 text-xs font-medium border-b-2 transition-colors',
              tab === t.key
                ? 'border-primary text-primary'
                : 'border-transparent text-muted-foreground hover:text-foreground'
            )}
          >
            <t.icon className="h-3.5 w-3.5" />
            {t.label}
          </button>
        ))}
      </div>

      <div className="flex-1 overflow-y-auto">
        <div className="p-6">
          {tab === 'general' && <GeneralSettings />}
          {tab === 'outlets' && <OutletSettings />}
          {tab === 'regions' && <RegionSettings />}
          {tab === 'branding' && <BrandingSettings />}
          {tab === 'preferences' && <PreferencesSettings />}
        </div>
      </div>
    </div>
  );
}
