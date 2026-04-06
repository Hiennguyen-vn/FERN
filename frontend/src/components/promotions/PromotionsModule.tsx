import { useState, useMemo } from 'react';
import {
  Tag, Percent, Clock, BarChart3, Plus, Pause, Play, Calendar,
  Search, Trash2, Copy, Edit, TrendingUp, AlertTriangle,
  ArrowLeft, CheckCircle2, XCircle, Info,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid, Legend } from 'recharts';
import { mockPromotions, mockPromotionPerformance } from '@/data/mock-promotions';
import type { Promotion, PromotionType, PromotionStatus, DayOfWeek } from '@/types/promotions';
import { toast } from 'sonner';

/* ─── Config ─── */
const statusConfig: Record<string, { label: string; class: string }> = {
  draft: { label: 'Draft', class: 'bg-muted text-muted-foreground' },
  scheduled: { label: 'Scheduled', class: 'bg-info/10 text-info' },
  active: { label: 'Active', class: 'bg-success/10 text-success' },
  paused: { label: 'Paused', class: 'bg-warning/10 text-warning' },
  expired: { label: 'Expired', class: 'bg-destructive/10 text-destructive' },
};

const typeLabels: Record<string, string> = {
  combo: 'Combo', discount_percent: 'Discount %', discount_fixed: 'Fixed Discount',
  bogo: 'Buy 1 Get 1', happy_hour: 'Happy Hour', bundle: 'Bundle', free_item: 'Free Item',
};

const dayLabels: Record<string, string> = {
  mon: 'Mon', tue: 'Tue', wed: 'Wed', thu: 'Thu', fri: 'Fri', sat: 'Sat', sun: 'Sun',
};
const ALL_DAYS: DayOfWeek[] = ['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun'];

const emptyPromo = (): Partial<Promotion> => ({
  name: '', code: '', type: 'discount_percent', status: 'draft', description: '',
  discountValue: 0, discountType: 'percent', minOrderValue: 0, maxDiscount: undefined,
  scope: 'all', appliedOutlets: [], appliedRegions: [],
  startDate: '', endDate: '', activeDays: [...ALL_DAYS], startTime: '', endTime: '',
  maxUsage: undefined, maxPerCustomer: undefined,
  applicableCategories: [], applicableItems: [],
});

/* ─── Main ─── */
export function PromotionsModule() {
  const [promos, setPromos] = useState<Promotion[]>(mockPromotions);
  const [selected, setSelected] = useState<Promotion | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [formData, setFormData] = useState<Partial<Promotion>>(emptyPromo());
  const [editingId, setEditingId] = useState<string | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('all');

  const filtered = useMemo(() => {
    let list = promos;
    if (statusFilter !== 'all') list = list.filter(p => p.status === statusFilter);
    if (search) list = list.filter(p =>
      p.name.toLowerCase().includes(search.toLowerCase()) ||
      p.code.toLowerCase().includes(search.toLowerCase())
    );
    return list;
  }, [promos, statusFilter, search]);

  const stats = useMemo(() => ({
    active: promos.filter(p => p.status === 'active').length,
    scheduled: promos.filter(p => p.status === 'scheduled').length,
    paused: promos.filter(p => p.status === 'paused').length,
    draft: promos.filter(p => p.status === 'draft').length,
    totalDiscount: promos.reduce((s, p) => s + p.totalDiscount, 0),
    totalOrders: promos.reduce((s, p) => s + p.ordersUsed, 0),
  }), [promos]);

  const openCreate = () => { setFormData(emptyPromo()); setEditingId(null); setFormOpen(true); };
  const openEdit = (p: Promotion) => { setFormData({ ...p }); setEditingId(p.id); setFormOpen(true); setSelected(null); };

  const handleSave = () => {
    if (!formData.name || !formData.code || !formData.startDate || !formData.endDate) {
      toast.error('Please fill in all required fields'); return;
    }
    if (editingId) {
      setPromos(prev => prev.map(p => p.id === editingId ? { ...p, ...formData } as Promotion : p));
      toast.success('Promotion updated');
    } else {
      const newPromo: Promotion = {
        ...formData as Promotion,
        id: `promo-${Date.now()}`,
        usageCount: 0, totalRevenue: 0, totalDiscount: 0, ordersUsed: 0,
        createdAt: new Date().toISOString().split('T')[0], createdBy: 'Current User',
      };
      setPromos(prev => [newPromo, ...prev]);
      toast.success('Promotion created');
    }
    setFormOpen(false);
  };

  const handleStatusChange = (id: string, newStatus: PromotionStatus) => {
    setPromos(prev => prev.map(p => p.id === id ? { ...p, status: newStatus } : p));
    toast.success(newStatus === 'active' ? 'Activated' : newStatus === 'paused' ? 'Paused' : 'Updated');
    if (selected?.id === id) setSelected(prev => prev ? { ...prev, status: newStatus } : null);
  };

  const handleDelete = (id: string) => {
    setPromos(prev => prev.filter(p => p.id !== id));
    setDeleteConfirm(null); setSelected(null);
    toast.success('Promotion deleted');
  };

  const handleDuplicate = (p: Promotion) => {
    const dup: Promotion = {
      ...p, id: `promo-${Date.now()}`, name: `${p.name} (Copy)`, code: `${p.code}-COPY`,
      status: 'draft', usageCount: 0, totalRevenue: 0, totalDiscount: 0, ordersUsed: 0,
      createdAt: new Date().toISOString().split('T')[0],
    };
    setPromos(prev => [dup, ...prev]);
    toast.success('Promotion duplicated');
  };

  const updateField = <K extends keyof Promotion>(key: K, val: Promotion[K]) =>
    setFormData(prev => ({ ...prev, [key]: val }));

  const toggleDay = (day: DayOfWeek) => {
    setFormData(prev => {
      const days = prev.activeDays || [];
      return { ...prev, activeDays: days.includes(day) ? days.filter(d => d !== day) : [...days, day] };
    });
  };

  // Detail view
  if (selected) {
    const chartData = mockPromotionPerformance
      .filter(p => p.promotionId === selected.id)
      .map(p => ({ date: p.date.slice(5), Revenue: p.revenue / 1000, Discount: p.discountGiven / 1000 }));

    const usagePct = selected.maxUsage ? (selected.usageCount / selected.maxUsage) * 100 : null;

    return (
      <div className="p-6 space-y-5 animate-fade-in">
        <button onClick={() => setSelected(null)} className="text-xs text-muted-foreground hover:text-foreground flex items-center gap-1 transition-colors">
          <ArrowLeft className="h-3 w-3" /> Back to promotions
        </button>

        {/* Header Card */}
        <div className="surface-elevated p-5">
          <div className="flex items-start justify-between">
            <div className="flex items-start gap-3">
              <div className={cn('h-10 w-10 rounded-lg flex items-center justify-center flex-shrink-0', selected.status === 'active' ? 'bg-success/10' : 'bg-muted/50')}>
                <Tag className={cn('h-5 w-5', selected.status === 'active' ? 'text-success' : 'text-muted-foreground')} />
              </div>
              <div>
                <div className="flex items-center gap-2.5">
                  <h2 className="text-lg font-semibold text-foreground">{selected.name}</h2>
                  <span className="font-mono text-xs text-primary bg-primary/10 px-1.5 py-0.5 rounded">{selected.code}</span>
                  <span className={cn('text-[10px] px-2.5 py-1 rounded-full font-medium', statusConfig[selected.status]?.class)}>{statusConfig[selected.status]?.label}</span>
                </div>
                <div className="flex items-center gap-3 mt-1.5 text-xs text-muted-foreground">
                  <span className="flex items-center gap-1"><Calendar className="h-3 w-3" /> {selected.startDate} → {selected.endDate}</span>
                  <span>· {typeLabels[selected.type]}</span>
                  {selected.startTime && <span>· ⏰ {selected.startTime}–{selected.endTime}</span>}
                </div>
              </div>
            </div>
            <div className="flex items-center gap-2 flex-shrink-0">
              {selected.status === 'active' && (
                <Button variant="outline" size="sm" className="h-8 text-xs" onClick={() => handleStatusChange(selected.id, 'paused')}>
                  <Pause className="h-3.5 w-3.5 mr-1.5" /> Pause
                </Button>
              )}
              {(selected.status === 'paused' || selected.status === 'draft') && (
                <Button size="sm" className="h-8 text-xs" onClick={() => handleStatusChange(selected.id, 'active')}>
                  <Play className="h-3.5 w-3.5 mr-1.5" /> Activate
                </Button>
              )}
              <Button variant="outline" size="sm" className="h-8 text-xs" onClick={() => openEdit(selected)}>
                <Edit className="h-3.5 w-3.5 mr-1.5" /> Edit
              </Button>
              <Button variant="outline" size="sm" className="h-8 text-xs" onClick={() => { handleDuplicate(selected); setSelected(null); }}>
                <Copy className="h-3.5 w-3.5 mr-1.5" /> Duplicate
              </Button>
              <Button variant="destructive" size="icon" className="h-8 w-8" onClick={() => setDeleteConfirm(selected.id)}>
                <Trash2 className="h-3.5 w-3.5" />
              </Button>
            </div>
          </div>
        </div>

        {/* KPIs */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
          {[
            { label: 'Discount', value: selected.discountType === 'percent' ? `${selected.discountValue}%` : `${(selected.discountValue / 1000).toFixed(0)}K`, icon: Percent },
            { label: 'Orders Used', value: selected.ordersUsed.toLocaleString(), icon: BarChart3 },
            { label: 'Revenue', value: `${(selected.totalRevenue / 1000000).toFixed(1)}M`, icon: TrendingUp },
            { label: 'Discounted', value: `${(selected.totalDiscount / 1000000).toFixed(1)}M`, icon: Tag },
          ].map(k => (
            <div key={k.label} className="surface-elevated p-4">
              <div className="flex items-center gap-1.5 mb-2">
                <k.icon className="h-3.5 w-3.5 text-muted-foreground" />
                <span className="text-[10px] font-medium text-muted-foreground uppercase tracking-wide">{k.label}</span>
              </div>
              <p className="text-xl font-semibold text-foreground">{k.value}</p>
            </div>
          ))}
        </div>

        {/* Usage Progress */}
        {usagePct !== null && (
          <div className="surface-elevated p-4">
            <div className="flex justify-between text-xs mb-2">
              <span className="text-muted-foreground">Usage Progress</span>
              <span className="font-medium font-mono">{selected.usageCount}/{selected.maxUsage}</span>
            </div>
            <div className="h-2 w-full bg-muted/30 rounded-full overflow-hidden">
              <div className="h-full bg-primary rounded-full transition-all" style={{ width: `${Math.min(usagePct, 100)}%` }} />
            </div>
          </div>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
          <div className="lg:col-span-2 space-y-5">
            {/* Schedule */}
            <div className="surface-elevated overflow-hidden">
              <div className="px-4 py-3 border-b"><span className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wide">Schedule</span></div>
              <div className="p-4 space-y-3">
                <p className="text-sm text-foreground">{selected.startDate} → {selected.endDate}</p>
                {selected.startTime && <p className="text-sm text-muted-foreground">⏰ {selected.startTime} – {selected.endTime}</p>}
                <div className="flex gap-1.5">{selected.activeDays.map(d => (
                  <span key={d} className="text-[10px] px-2 py-0.5 rounded-full bg-muted text-muted-foreground font-medium">{dayLabels[d]}</span>
                ))}</div>
              </div>
            </div>

            {/* Chart */}
            {chartData.length > 0 && (
              <div className="surface-elevated overflow-hidden">
                <div className="px-4 py-3 border-b"><span className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wide">Daily Performance</span></div>
                <div className="p-4 h-48">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={chartData}>
                      <CartesianGrid strokeDasharray="3 3" className="stroke-border" />
                      <XAxis dataKey="date" className="text-xs" />
                      <YAxis className="text-xs" />
                      <Tooltip />
                      <Legend />
                      <Bar dataKey="Revenue" fill="hsl(var(--primary))" radius={[2, 2, 0, 0]} />
                      <Bar dataKey="Discount" fill="hsl(var(--destructive))" radius={[2, 2, 0, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              </div>
            )}
          </div>

          {/* Sidebar */}
          <div className="space-y-4">
            <div className="surface-elevated overflow-hidden">
              <div className="px-4 py-3 border-b"><span className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wide">Scope</span></div>
              <div className="p-4 space-y-2 text-sm">
                <p>{selected.scope === 'all' ? '🌐 System-wide' : selected.scope === 'region' ? `📍 ${selected.appliedRegions.join(', ')}` : `🏪 ${selected.appliedOutlets.join(', ')}`}</p>
                {selected.applicableCategories.length > 0 && <p className="text-xs text-muted-foreground">📦 {selected.applicableCategories.join(', ')}</p>}
                {selected.minOrderValue ? <p className="text-xs text-muted-foreground">Min. order: {(selected.minOrderValue / 1000).toFixed(0)}K</p> : null}
                {selected.maxDiscount ? <p className="text-xs text-muted-foreground">Max discount: {(selected.maxDiscount / 1000).toFixed(0)}K</p> : null}
                {selected.maxPerCustomer ? <p className="text-xs text-muted-foreground">Limit/customer: {selected.maxPerCustomer}</p> : null}
              </div>
            </div>
            <div className="surface-elevated overflow-hidden">
              <div className="px-4 py-3 border-b"><span className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wide">Details</span></div>
              <div className="p-4 space-y-3">
                {[
                  { label: 'Promo ID', value: selected.id },
                  { label: 'Created By', value: selected.createdBy },
                  { label: 'Created At', value: selected.createdAt },
                ].map(item => (
                  <div key={item.label}>
                    <p className="text-[10px] text-muted-foreground uppercase tracking-wide">{item.label}</p>
                    <p className="text-xs font-medium text-foreground mt-0.5 font-mono">{item.value}</p>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>

        {/* Delete Confirmation */}
        <Dialog open={!!deleteConfirm} onOpenChange={() => setDeleteConfirm(null)}>
          <DialogContent className="sm:max-w-md">
            <DialogHeader>
              <DialogTitle className="flex items-center gap-2"><AlertTriangle className="h-5 w-5 text-destructive" /> Confirm Delete</DialogTitle>
            </DialogHeader>
            <p className="text-sm text-muted-foreground">Are you sure you want to delete this promotion? This action cannot be undone.</p>
            <DialogFooter>
              <Button variant="outline" size="sm" onClick={() => setDeleteConfirm(null)}>Cancel</Button>
              <Button variant="destructive" size="sm" onClick={() => deleteConfirm && handleDelete(deleteConfirm)}>Delete</Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>
    );
  }

  // ── List View ──
  return (
    <div className="p-6 space-y-5 animate-fade-in">
      {/* Header */}
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-lg font-semibold text-foreground">Promotions</h2>
          <p className="text-xs text-muted-foreground mt-0.5">Manage promotion campaigns and track performance</p>
        </div>
        <Button size="sm" className="h-8 text-xs gap-1.5" onClick={openCreate}>
          <Plus className="h-3.5 w-3.5" /> New Promotion
        </Button>
      </div>

      {/* KPIs */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        {[
          { label: 'Active', value: stats.active, icon: Tag, color: 'text-success' },
          { label: 'Total Discount', value: `${(stats.totalDiscount / 1000000).toFixed(1)}M`, icon: Percent, color: 'text-destructive' },
          { label: 'Orders with Promo', value: stats.totalOrders.toLocaleString(), icon: BarChart3, color: 'text-foreground' },
          { label: 'Draft & Scheduled', value: stats.draft + stats.scheduled, icon: Clock, color: 'text-muted-foreground' },
        ].map(k => (
          <div key={k.label} className="surface-elevated p-4">
            <div className="flex items-center gap-1.5 mb-2">
              <k.icon className="h-3.5 w-3.5 text-muted-foreground" />
              <span className="text-[10px] font-medium text-muted-foreground uppercase tracking-wide">{k.label}</span>
            </div>
            <p className={cn('text-xl font-semibold', k.color)}>{k.value}</p>
          </div>
        ))}
      </div>

      {/* Filters */}
      <div className="flex items-center gap-3 flex-wrap">
        <div className="relative flex-1 max-w-xs">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
          <Input placeholder="Search by name or code…" value={search} onChange={e => setSearch(e.target.value)} className="pl-9 h-8 text-sm" />
        </div>
        <div className="flex items-center gap-1.5">
          {['all', 'active', 'draft', 'scheduled', 'paused', 'expired'].map(s => (
            <button key={s} onClick={() => setStatusFilter(s)} className={cn('text-[11px] px-2.5 py-1.5 rounded-md border transition-colors capitalize', statusFilter === s ? 'bg-primary text-primary-foreground border-primary' : 'bg-card text-foreground hover:bg-accent border-border')}>{s}</button>
          ))}
        </div>
      </div>

      {/* Table */}
      <div className="surface-elevated overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="border-b bg-muted/30">
              {['Promotion', 'Type', 'Status', 'Scope', 'Schedule', 'Usage', 'Revenue', 'Discounted', ''].map(h => (
                <th key={h} className={cn('text-[11px] font-medium text-muted-foreground px-4 py-2.5', ['Revenue', 'Discounted', 'Usage'].includes(h) ? 'text-right' : 'text-left')}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {filtered.length === 0 && (
              <tr><td colSpan={9} className="px-4 py-12 text-center text-sm text-muted-foreground">No promotions found</td></tr>
            )}
            {filtered.map(p => (
              <tr key={p.id} className="border-b last:border-0 hover:bg-muted/20 cursor-pointer transition-colors" onClick={() => setSelected(p)}>
                <td className="px-4 py-2.5">
                  <p className="text-sm font-medium text-foreground">{p.name}</p>
                  <p className="text-[10px] text-muted-foreground font-mono">{p.code}</p>
                </td>
                <td className="px-4 py-2.5"><span className="text-[10px] px-2 py-0.5 rounded-full font-medium bg-muted text-muted-foreground">{typeLabels[p.type]}</span></td>
                <td className="px-4 py-2.5"><span className={cn('text-[10px] px-2 py-0.5 rounded-full font-medium', statusConfig[p.status]?.class)}>{statusConfig[p.status]?.label}</span></td>
                <td className="px-4 py-2.5 text-xs text-muted-foreground">{p.scope === 'all' ? 'System-wide' : p.scope === 'region' ? p.appliedRegions.join(', ') : p.appliedOutlets.join(', ')}</td>
                <td className="px-4 py-2.5 text-xs text-muted-foreground">{p.startDate} → {p.endDate}</td>
                <td className="px-4 py-2.5 text-right">
                  {p.maxUsage ? (
                    <div className="space-y-1">
                      <span className="text-xs font-mono">{p.usageCount}/{p.maxUsage}</span>
                      <div className="h-1.5 w-16 bg-muted/30 rounded-full overflow-hidden ml-auto">
                        <div className="h-full bg-primary rounded-full" style={{ width: `${Math.min((p.usageCount / p.maxUsage) * 100, 100)}%` }} />
                      </div>
                    </div>
                  ) : <span className="text-sm">{p.usageCount.toLocaleString()}</span>}
                </td>
                <td className="px-4 py-2.5 text-right font-mono text-sm font-medium">{(p.totalRevenue / 1000000).toFixed(1)}M</td>
                <td className="px-4 py-2.5 text-right font-mono text-sm text-destructive">{(p.totalDiscount / 1000000).toFixed(1)}M</td>
                <td className="px-4 py-2.5">
                  <div className="flex gap-1" onClick={e => e.stopPropagation()}>
                    {p.status === 'active' && (
                      <button className="h-7 w-7 rounded-md hover:bg-muted flex items-center justify-center transition-colors" onClick={() => handleStatusChange(p.id, 'paused')}>
                        <Pause className="h-3.5 w-3.5 text-muted-foreground" />
                      </button>
                    )}
                    {(p.status === 'paused' || p.status === 'draft') && (
                      <button className="h-7 w-7 rounded-md hover:bg-muted flex items-center justify-center transition-colors" onClick={() => handleStatusChange(p.id, 'active')}>
                        <Play className="h-3.5 w-3.5 text-muted-foreground" />
                      </button>
                    )}
                    <button className="h-7 w-7 rounded-md hover:bg-muted flex items-center justify-center transition-colors" onClick={() => handleDuplicate(p)}>
                      <Copy className="h-3.5 w-3.5 text-muted-foreground" />
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Create/Edit Dialog */}
      <Dialog open={formOpen} onOpenChange={setFormOpen}>
        <DialogContent className="sm:max-w-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{editingId ? 'Edit Promotion' : 'Create New Promotion'}</DialogTitle>
          </DialogHeader>
          <div className="space-y-5 py-2">
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label className="text-xs">Promotion Name *</Label>
                <Input value={formData.name || ''} onChange={e => updateField('name', e.target.value)} placeholder="e.g. Happy Hour Drinks" className="h-8 text-sm" />
              </div>
              <div className="space-y-2">
                <Label className="text-xs">Promo Code *</Label>
                <Input value={formData.code || ''} onChange={e => updateField('code', e.target.value.toUpperCase())} placeholder="e.g. HAPPYHOUR" className="h-8 text-sm font-mono" />
              </div>
            </div>
            <div className="space-y-2">
              <Label className="text-xs">Description</Label>
              <Textarea value={formData.description || ''} onChange={e => updateField('description', e.target.value)} placeholder="Describe the promotion…" rows={2} className="text-sm" />
            </div>
            <div className="grid grid-cols-3 gap-4">
              <div className="space-y-2">
                <Label className="text-xs">Promotion Type</Label>
                <Select value={formData.type || 'discount_percent'} onValueChange={v => updateField('type', v as PromotionType)}>
                  <SelectTrigger className="h-8 text-sm"><SelectValue /></SelectTrigger>
                  <SelectContent>{Object.entries(typeLabels).map(([k, v]) => <SelectItem key={k} value={k}>{v}</SelectItem>)}</SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label className="text-xs">Discount Type</Label>
                <Select value={formData.discountType || 'percent'} onValueChange={v => updateField('discountType', v as 'percent' | 'fixed')}>
                  <SelectTrigger className="h-8 text-sm"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="percent">Percentage (%)</SelectItem>
                    <SelectItem value="fixed">Fixed Amount (₫)</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label className="text-xs">Discount Value *</Label>
                <Input type="number" value={formData.discountValue || ''} onChange={e => updateField('discountValue', Number(e.target.value))} className="h-8 text-sm" />
              </div>
            </div>
            <div className="grid grid-cols-3 gap-4">
              <div className="space-y-2">
                <Label className="text-xs">Min. Order (₫)</Label>
                <Input type="number" value={formData.minOrderValue || ''} onChange={e => updateField('minOrderValue', Number(e.target.value))} placeholder="0" className="h-8 text-sm" />
              </div>
              <div className="space-y-2">
                <Label className="text-xs">Max Discount (₫)</Label>
                <Input type="number" value={formData.maxDiscount || ''} onChange={e => updateField('maxDiscount', Number(e.target.value))} placeholder="Unlimited" className="h-8 text-sm" />
              </div>
              <div className="space-y-2">
                <Label className="text-xs">Limit / Customer</Label>
                <Input type="number" value={formData.maxPerCustomer || ''} onChange={e => updateField('maxPerCustomer', Number(e.target.value))} placeholder="Unlimited" className="h-8 text-sm" />
              </div>
            </div>

            <div className="space-y-3">
              <Label className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Schedule</Label>
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label className="text-xs">Start Date *</Label>
                  <Input type="date" value={formData.startDate || ''} onChange={e => updateField('startDate', e.target.value)} className="h-8 text-sm" />
                </div>
                <div className="space-y-2">
                  <Label className="text-xs">End Date *</Label>
                  <Input type="date" value={formData.endDate || ''} onChange={e => updateField('endDate', e.target.value)} className="h-8 text-sm" />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label className="text-xs">Start Time</Label>
                  <Input type="time" value={formData.startTime || ''} onChange={e => updateField('startTime', e.target.value)} className="h-8 text-sm" />
                </div>
                <div className="space-y-2">
                  <Label className="text-xs">End Time</Label>
                  <Input type="time" value={formData.endTime || ''} onChange={e => updateField('endTime', e.target.value)} className="h-8 text-sm" />
                </div>
              </div>
              <div className="space-y-2">
                <Label className="text-xs">Active Days</Label>
                <div className="flex gap-1.5">
                  {ALL_DAYS.map(d => (
                    <button key={d} type="button" onClick={() => toggleDay(d)} className={cn('px-2.5 py-1 rounded-md text-[11px] font-medium border transition-colors', formData.activeDays?.includes(d) ? 'bg-primary text-primary-foreground border-primary' : 'bg-background text-muted-foreground border-border hover:bg-muted')}>{dayLabels[d]}</button>
                  ))}
                </div>
              </div>
            </div>

            <div className="space-y-3">
              <Label className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Scope</Label>
              <Select value={formData.scope || 'all'} onValueChange={v => updateField('scope', v as 'all' | 'region' | 'outlet')}>
                <SelectTrigger className="h-8 text-sm"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">System-wide</SelectItem>
                  <SelectItem value="region">By Region</SelectItem>
                  <SelectItem value="outlet">By Outlet</SelectItem>
                </SelectContent>
              </Select>
              {formData.scope === 'region' && <Input placeholder="Region names (comma separated)" value={formData.appliedRegions?.join(', ') || ''} onChange={e => updateField('appliedRegions', e.target.value.split(',').map(s => s.trim()).filter(Boolean))} className="h-8 text-sm" />}
              {formData.scope === 'outlet' && <Input placeholder="Outlet names (comma separated)" value={formData.appliedOutlets?.join(', ') || ''} onChange={e => updateField('appliedOutlets', e.target.value.split(',').map(s => s.trim()).filter(Boolean))} className="h-8 text-sm" />}
            </div>

            <div className="space-y-2">
              <Label className="text-xs">Total Usage Limit</Label>
              <Input type="number" value={formData.maxUsage || ''} onChange={e => updateField('maxUsage', Number(e.target.value) || undefined)} placeholder="Unlimited" className="h-8 text-sm" />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" size="sm" onClick={() => setFormOpen(false)}>Cancel</Button>
            {!editingId && <Button variant="outline" size="sm" onClick={() => { updateField('status', 'draft'); handleSave(); }}>Save as Draft</Button>}
            <Button size="sm" onClick={handleSave}>{editingId ? 'Update' : 'Create & Activate'}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Delete Confirmation */}
      <Dialog open={!!deleteConfirm} onOpenChange={() => setDeleteConfirm(null)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2"><AlertTriangle className="h-5 w-5 text-destructive" /> Confirm Delete</DialogTitle>
          </DialogHeader>
          <p className="text-sm text-muted-foreground">Are you sure you want to delete this promotion? This action cannot be undone.</p>
          <DialogFooter>
            <Button variant="outline" size="sm" onClick={() => setDeleteConfirm(null)}>Cancel</Button>
            <Button variant="destructive" size="sm" onClick={() => deleteConfirm && handleDelete(deleteConfirm)}>Delete</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
