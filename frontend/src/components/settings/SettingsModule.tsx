import { useEffect, useMemo, useState } from 'react';
import {
  Building2, MapPin, Globe, Palette, Settings, Plus, Edit2, ChevronRight, Loader2, ToggleLeft, ToggleRight,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { toast } from 'sonner';
import { createOutlet, listOutlets, listRegions, updateOutlet } from '@/lib/api/org';
import type { OutletResponse, RegionResponse } from '@/lib/api/types';

type SettingsTab = 'general' | 'outlets' | 'regions' | 'branding' | 'preferences';
type OutletStatus = 'DRAFT' | 'ACTIVE' | 'INACTIVE' | 'CLOSING' | 'CLOSED';

const TABS: { key: SettingsTab; label: string; icon: React.ElementType }[] = [
  { key: 'general', label: 'General', icon: Settings },
  { key: 'outlets', label: 'Outlets', icon: Building2 },
  { key: 'regions', label: 'Regions', icon: MapPin },
  { key: 'branding', label: 'Branding', icon: Palette },
  { key: 'preferences', label: 'Preferences', icon: Globe },
];

const OUTLET_STATUSES: OutletStatus[] = ['ACTIVE', 'INACTIVE', 'CLOSING', 'CLOSED', 'DRAFT'];

const PREFERENCES = [
  { key: 'timezone', label: 'Default Timezone', value: 'Asia/Singapore (UTC+8)', type: 'select' },
  { key: 'currency', label: 'Default Currency', value: 'USD - US Dollar', type: 'select' },
  { key: 'date_format', label: 'Date Format', value: 'YYYY-MM-DD', type: 'select' },
  { key: 'language', label: 'Language', value: 'English (US)', type: 'select' },
  { key: 'session_timeout', label: 'Session Timeout', value: '30 minutes', type: 'select' },
  { key: 'auto_logout', label: 'Auto Logout on Idle', value: true, type: 'toggle' },
  { key: 'email_notifications', label: 'Email Notifications', value: true, type: 'toggle' },
  { key: 'two_factor', label: 'Require 2FA for Managers', value: false, type: 'toggle' },
];

interface OutletRow extends OutletResponse {
  regionName?: string;
}

function statusLabel(value: string): string {
  return value.toLowerCase().replace(/_/g, ' ').replace(/\b\w/g, (m) => m.toUpperCase());
}

function isConflictError(error: unknown): boolean {
  const code = (error as { code?: string | number } | null)?.code;
  if (String(code) === '409') return true;
  const message = error instanceof Error ? error.message : '';
  return /already exists|conflict/i.test(message);
}

function slugifyCode(name: string): string {
  return (
    name
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toUpperCase()
      .replace(/[^A-Z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .slice(0, 24) || 'OUTLET'
  );
}

function buildOutletCode(name: string, attempt: number): string {
  if (attempt === 0) return slugifyCode(name);
  const suffix = Math.random().toString(36).slice(2, 6).toUpperCase();
  return `${slugifyCode(name)}-${suffix}`;
}

function StatusBadge({ status }: { status: string }) {
  const normalized = String(status || 'ACTIVE').toUpperCase();
  const colorMap: Record<string, string> = {
    ACTIVE: 'bg-success/10 text-success',
    CLOSING: 'bg-warning/10 text-warning',
    CLOSED: 'bg-muted text-muted-foreground',
    INACTIVE: 'bg-muted text-muted-foreground',
    DRAFT: 'bg-primary/10 text-primary',
  };
  return (
    <span className={`text-[10px] px-2 py-0.5 rounded-full font-medium ${colorMap[normalized] || colorMap.ACTIVE}`}>
      {statusLabel(normalized)}
    </span>
  );
}

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
    </div>
  );
}

function OutletSettings() {
  const [outlets, setOutlets] = useState<OutletRow[]>([]);
  const [regions, setRegions] = useState<RegionResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<OutletRow | null>(null);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState({ name: '', regionId: '', address: '', status: 'ACTIVE' as OutletStatus });

  const fetchData = async () => {
    setLoading(true);
    try {
      const [outletPage, regionPage] = await Promise.all([listOutlets({ size: 1000 }), listRegions({ size: 500 })]);
      const regionMap = new Map(regionPage.items.map((region) => [region.id, region.name]));
      const mapped = outletPage.items.map((outlet) => ({ ...outlet, regionName: regionMap.get(outlet.regionId) }));
      mapped.sort((a, b) => a.name.localeCompare(b.name));
      setOutlets(mapped);
      setRegions(regionPage.items);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to load outlets');
      setOutlets([]);
      setRegions([]);
    }
    setLoading(false);
  };

  useEffect(() => {
    void fetchData();
  }, []);

  const openCreate = () => {
    setEditing(null);
    setForm({ name: '', regionId: String(regions[0]?.id || ''), address: '', status: 'ACTIVE' });
    setDialogOpen(true);
  };

  const openEdit = (outlet: OutletRow) => {
    setEditing(outlet);
    setForm({
      name: outlet.name,
      regionId: String(outlet.regionId),
      address: outlet.address || '',
      status: (String(outlet.status).toUpperCase() as OutletStatus) || 'ACTIVE',
    });
    setDialogOpen(true);
  };

  const handleSave = async () => {
    if (!form.name.trim()) {
      toast.error('Name is required');
      return;
    }
    if (!form.regionId) {
      toast.error('Region is required');
      return;
    }

    setSaving(true);
    try {
      const regionId = Number(form.regionId);
      if (editing) {
        await updateOutlet(editing.id, {
          name: form.name.trim(),
          regionId,
          address: form.address || undefined,
          status: form.status,
        });
        toast.success('Outlet updated');
      } else {
        let created = false;
        let lastError: unknown;
        for (let attempt = 0; attempt < 5 && !created; attempt += 1) {
          try {
            await createOutlet({
              regionId,
              code: buildOutletCode(form.name, attempt),
              name: form.name.trim(),
              status: form.status,
              address: form.address || undefined,
            });
            created = true;
          } catch (error) {
            lastError = error;
            if (!isConflictError(error)) break;
          }
        }
        if (!created) throw lastError || new Error('Failed to create outlet');
        toast.success('Outlet created');
      }
      setDialogOpen(false);
      await fetchData();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to save outlet');
    }
    setSaving(false);
  };

  return (
    <div className="space-y-5">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-lg font-semibold text-foreground">Outlet Management</h2>
          <p className="text-xs text-muted-foreground mt-0.5">{outlets.length} outlets across {regions.length} regions</p>
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
                {['Outlet', 'Region', 'Address', 'Status', ''].map((h) => (
                  <th key={h} className="text-[11px] font-medium text-muted-foreground px-4 py-2.5 text-left">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {outlets.map((outlet) => (
                <tr
                  key={outlet.id}
                  className="border-b last:border-0 hover:bg-muted/20 transition-colors cursor-pointer"
                  onClick={() => openEdit(outlet)}
                >
                  <td className="px-4 py-2.5 text-sm font-medium text-foreground">{outlet.name}</td>
                  <td className="px-4 py-2.5 text-xs text-muted-foreground">{outlet.regionName || `Region #${outlet.regionId}`}</td>
                  <td className="px-4 py-2.5 text-xs text-muted-foreground">{outlet.address || '-'}</td>
                  <td className="px-4 py-2.5"><StatusBadge status={outlet.status} /></td>
                  <td className="px-4 py-2.5"><ChevronRight className="h-3.5 w-3.5 text-muted-foreground" /></td>
                </tr>
              ))}
              {outlets.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-4 py-8 text-center text-sm text-muted-foreground">No outlets yet. Create one to get started.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{editing ? 'Edit Outlet' : 'Create Outlet'}</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div>
              <Label className="text-xs">Name *</Label>
              <Input
                className="mt-1"
                value={form.name}
                onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value }))}
                placeholder="e.g. Downtown Flagship"
              />
            </div>
            <div>
              <Label className="text-xs">Region *</Label>
              <select
                value={form.regionId}
                onChange={(e) => setForm((prev) => ({ ...prev, regionId: e.target.value }))}
                className="mt-1 w-full h-10 rounded-md border border-input bg-background px-3 text-sm"
              >
                <option value="" disabled>Select region</option>
                {regions.map((region) => (
                  <option key={region.id} value={String(region.id)}>{region.name}</option>
                ))}
              </select>
            </div>
            <div>
              <Label className="text-xs">Address</Label>
              <Input
                className="mt-1"
                value={form.address}
                onChange={(e) => setForm((prev) => ({ ...prev, address: e.target.value }))}
                placeholder="e.g. 123 Main St"
              />
            </div>
            <div>
              <Label className="text-xs">Status</Label>
              <select
                value={form.status}
                onChange={(e) => setForm((prev) => ({ ...prev, status: e.target.value as OutletStatus }))}
                className="mt-1 w-full h-10 rounded-md border border-input bg-background px-3 text-sm"
              >
                {OUTLET_STATUSES.map((status) => (
                  <option key={status} value={status}>{statusLabel(status)}</option>
                ))}
              </select>
            </div>
          </div>
          <DialogFooter>
            <div className="flex gap-2">
              <Button variant="outline" size="sm" className="h-8 text-xs" onClick={() => setDialogOpen(false)}>Cancel</Button>
              <Button size="sm" className="h-8 text-xs" onClick={handleSave} disabled={saving}>
                {saving ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : (editing ? 'Save Changes' : 'Create')}
              </Button>
            </div>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function RegionSettings() {
  const [regions, setRegions] = useState<RegionResponse[]>([]);
  const [outlets, setOutlets] = useState<OutletResponse[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetch = async () => {
      setLoading(true);
      try {
        const [regionPage, outletPage] = await Promise.all([listRegions({ size: 500 }), listOutlets({ size: 1000 })]);
        setRegions(regionPage.items);
        setOutlets(outletPage.items);
      } catch (error) {
        toast.error(error instanceof Error ? error.message : 'Failed to load regions');
        setRegions([]);
        setOutlets([]);
      }
      setLoading(false);
    };
    void fetch();
  }, []);

  const grouped = useMemo(() => {
    const outletGroups = outlets.reduce<Record<number, OutletResponse[]>>((acc, outlet) => {
      acc[outlet.regionId] = acc[outlet.regionId] || [];
      acc[outlet.regionId].push(outlet);
      return acc;
    }, {});
    return regions.map((region) => ({
      region,
      outlets: (outletGroups[region.id] || []).sort((a, b) => a.name.localeCompare(b.name)),
    }));
  }, [regions, outlets]);

  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-lg font-semibold text-foreground">Region Management</h2>
        <p className="text-xs text-muted-foreground mt-0.5">Regions and their assigned outlets from backend data</p>
      </div>
      {loading ? (
        <div className="flex items-center justify-center py-12"><Loader2 className="h-5 w-5 animate-spin text-muted-foreground" /></div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {grouped.map(({ region, outlets: regionOutlets }) => (
            <div key={region.id} className="surface-elevated p-5 hover:shadow-md transition-shadow">
              <div className="flex items-center justify-between mb-3">
                <div className="h-9 w-9 rounded-lg bg-primary/10 flex items-center justify-center">
                  <MapPin className="h-4 w-4 text-primary" />
                </div>
                <span className="text-[10px] font-medium px-2 py-0.5 rounded-full bg-primary/10 text-primary">{region.code}</span>
              </div>
              <h3 className="text-sm font-semibold text-foreground">{region.name}</h3>
              <div className="mt-3 pt-3 border-t space-y-1">
                {regionOutlets.map((outlet) => (
                  <div key={outlet.id} className="flex items-center justify-between text-xs">
                    <span className="text-foreground">{outlet.name}</span>
                    <StatusBadge status={outlet.status} />
                  </div>
                ))}
              </div>
              <p className="text-[10px] text-muted-foreground mt-2">{regionOutlets.length} outlet{regionOutlets.length !== 1 ? 's' : ''}</p>
            </div>
          ))}
          {grouped.length === 0 && (
            <p className="col-span-3 text-center text-sm text-muted-foreground py-8">No regions found.</p>
          )}
        </div>
      )}
    </div>
  );
}

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
      </div>
    </div>
  );
}

function PreferencesSettings() {
  const [prefs, setPrefs] = useState(PREFERENCES);
  const togglePref = (key: string) => {
    setPrefs((prev) => prev.map((pref) => (pref.key === key ? { ...pref, value: !pref.value } : pref)));
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
          {prefs.map((pref) => (
            <div key={pref.key} className="flex items-center justify-between py-2.5 px-3 rounded-lg hover:bg-muted/20 transition-colors">
              <div>
                <p className="text-sm font-medium text-foreground">{pref.label}</p>
                <p className="text-[10px] font-mono text-muted-foreground mt-0.5">{pref.key}</p>
              </div>
              {pref.type === 'toggle' ? (
                <button onClick={() => togglePref(pref.key)} className="text-primary">
                  {pref.value ? <ToggleRight className="h-6 w-6" /> : <ToggleLeft className="h-6 w-6 text-muted-foreground" />}
                </button>
              ) : (
                <span className="text-xs text-foreground bg-muted px-2 py-0.5 rounded">{String(pref.value)}</span>
              )}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

export function SettingsModule() {
  const [tab, setTab] = useState<SettingsTab>('general');

  return (
    <div className="flex flex-col h-full animate-fade-in">
      <div className="border-b bg-card px-6 flex items-center gap-0 flex-shrink-0">
        {TABS.map((t) => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            className={cn(
              'flex items-center gap-1.5 px-4 py-3 text-xs font-medium border-b-2 transition-colors',
              tab === t.key ? 'border-primary text-primary' : 'border-transparent text-muted-foreground hover:text-foreground',
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
