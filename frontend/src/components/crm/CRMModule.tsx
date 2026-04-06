import { useState } from 'react';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Users, Star, Gift, Ticket, Search, Plus, TrendingUp, Award } from 'lucide-react';
import { mockCRMCustomers, mockPurchaseHistory, mockRewards, mockVouchers, mockTierConfigs, crmStats } from '@/data/mock-crm';
import type { CRMCustomer, Reward, Voucher } from '@/types/crm';

const tierColors: Record<string, string> = {
  bronze: 'bg-orange-100 text-orange-800',
  silver: 'bg-gray-100 text-gray-800',
  gold: 'bg-yellow-100 text-yellow-800',
  platinum: 'bg-purple-100 text-purple-800',
};

const voucherStatusColors: Record<string, string> = {
  active: 'bg-green-100 text-green-800',
  used: 'bg-blue-100 text-blue-800',
  expired: 'bg-red-100 text-red-800',
  cancelled: 'bg-gray-100 text-gray-800',
};

export function CRMModule() {
  const [search, setSearch] = useState('');
  const [selectedCustomer, setSelectedCustomer] = useState<CRMCustomer | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);

  const filteredCustomers = mockCRMCustomers.filter(c =>
    c.name.toLowerCase().includes(search.toLowerCase()) ||
    c.memberCode.toLowerCase().includes(search.toLowerCase()) ||
    c.phone.includes(search)
  );

  const openDetail = (c: CRMCustomer) => { setSelectedCustomer(c); setDetailOpen(true); };
  const customerHistory = selectedCustomer ? mockPurchaseHistory.filter(h => h.customerId === selectedCustomer.id) : [];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">CRM & Loyalty</h1>
          <p className="text-muted-foreground">Quản lý khách hàng, điểm tích lũy và chương trình loyalty</p>
        </div>
        <Button><Plus className="h-4 w-4 mr-2" /> Thêm khách hàng</Button>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <Card><CardContent className="pt-6"><div className="flex items-center gap-2"><Users className="h-5 w-5 text-muted-foreground" /><div><p className="text-2xl font-bold">{crmStats.totalMembers.toLocaleString()}</p><p className="text-xs text-muted-foreground">Tổng thành viên</p></div></div></CardContent></Card>
        <Card><CardContent className="pt-6"><div className="flex items-center gap-2"><TrendingUp className="h-5 w-5 text-muted-foreground" /><div><p className="text-2xl font-bold">+{crmStats.newThisMonth}</p><p className="text-xs text-muted-foreground">Mới tháng này</p></div></div></CardContent></Card>
        <Card><CardContent className="pt-6"><div className="flex items-center gap-2"><Star className="h-5 w-5 text-muted-foreground" /><div><p className="text-2xl font-bold">{crmStats.activeRate}%</p><p className="text-xs text-muted-foreground">Tỷ lệ hoạt động</p></div></div></CardContent></Card>
        <Card><CardContent className="pt-6"><div className="flex items-center gap-2"><Award className="h-5 w-5 text-muted-foreground" /><div><p className="text-2xl font-bold">{(crmStats.totalPointsCirculating / 1000).toFixed(0)}K</p><p className="text-xs text-muted-foreground">Điểm đang lưu hành</p></div></div></CardContent></Card>
      </div>

      <Tabs defaultValue="customers" className="space-y-4">
        <TabsList>
          <TabsTrigger value="customers"><Users className="h-4 w-4 mr-1" /> Khách hàng</TabsTrigger>
          <TabsTrigger value="tiers"><Star className="h-4 w-4 mr-1" /> Hạng thành viên</TabsTrigger>
          <TabsTrigger value="rewards"><Gift className="h-4 w-4 mr-1" /> Phần thưởng</TabsTrigger>
          <TabsTrigger value="vouchers"><Ticket className="h-4 w-4 mr-1" /> Voucher</TabsTrigger>
        </TabsList>

        {/* Customers Tab */}
        <TabsContent value="customers" className="space-y-4">
          <div className="flex gap-2">
            <div className="relative flex-1"><Search className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" /><Input placeholder="Tìm theo tên, mã thành viên, SĐT..." className="pl-9" value={search} onChange={e => setSearch(e.target.value)} /></div>
          </div>
          <Card>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Khách hàng</TableHead>
                  <TableHead>Mã TV</TableHead>
                  <TableHead>Hạng</TableHead>
                  <TableHead className="text-right">Điểm</TableHead>
                  <TableHead className="text-right">Tổng chi tiêu</TableHead>
                  <TableHead className="text-right">Lần ghé cuối</TableHead>
                  <TableHead>Outlet</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {filteredCustomers.map(c => (
                  <TableRow key={c.id} className="cursor-pointer" onClick={() => openDetail(c)}>
                    <TableCell>
                      <div><p className="font-medium">{c.name}</p><p className="text-xs text-muted-foreground">{c.phone}</p></div>
                    </TableCell>
                    <TableCell className="font-mono text-sm">{c.memberCode}</TableCell>
                    <TableCell><Badge className={tierColors[c.loyaltyTier]}>{c.loyaltyTier}</Badge></TableCell>
                    <TableCell className="text-right font-medium">{c.loyaltyPoints.toLocaleString()}</TableCell>
                    <TableCell className="text-right">{(c.totalSpend / 1000).toLocaleString()}K</TableCell>
                    <TableCell className="text-right text-sm">{c.lastVisit}</TableCell>
                    <TableCell className="text-sm text-muted-foreground">{c.outletName}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Card>
        </TabsContent>

        {/* Tiers Tab */}
        <TabsContent value="tiers" className="space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            {mockTierConfigs.map(t => (
              <Card key={t.tier}>
                <CardHeader>
                  <CardTitle className="flex items-center gap-2">
                    <div className="w-4 h-4 rounded-full" style={{ backgroundColor: t.color }} />
                    <span className="capitalize">{t.tier}</span>
                  </CardTitle>
                  <CardDescription>Từ {t.minPoints.toLocaleString()} điểm • x{t.pointsMultiplier} tích điểm</CardDescription>
                </CardHeader>
                <CardContent>
                  <p className="text-sm font-medium mb-2">Quyền lợi:</p>
                  <ul className="text-sm text-muted-foreground space-y-1">
                    {t.benefits.map((b, i) => <li key={i}>• {b}</li>)}
                  </ul>
                  <div className="mt-4 pt-4 border-t">
                    <p className="text-lg font-bold">{crmStats.tierDistribution[t.tier]}</p>
                    <p className="text-xs text-muted-foreground">thành viên</p>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        </TabsContent>

        {/* Rewards Tab */}
        <TabsContent value="rewards" className="space-y-4">
          <div className="flex justify-end"><Button><Plus className="h-4 w-4 mr-2" /> Tạo phần thưởng</Button></div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {mockRewards.map((r: Reward) => (
              <Card key={r.id}>
                <CardHeader>
                  <div className="flex items-center justify-between">
                    <CardTitle className="text-lg">{r.name}</CardTitle>
                    <Badge variant={r.isActive ? 'default' : 'secondary'}>{r.isActive ? 'Đang hoạt động' : 'Tạm ngưng'}</Badge>
                  </div>
                  <CardDescription>{r.description}</CardDescription>
                </CardHeader>
                <CardContent>
                  <div className="grid grid-cols-3 gap-4 text-sm">
                    <div><p className="text-muted-foreground">Điểm đổi</p><p className="font-bold">{r.pointsCost}</p></div>
                    <div><p className="text-muted-foreground">Đã đổi</p><p className="font-bold">{r.redemptionCount}</p></div>
                    <div><p className="text-muted-foreground">Giới hạn</p><p className="font-bold">{r.maxRedemptions ?? '∞'}</p></div>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        </TabsContent>

        {/* Vouchers Tab */}
        <TabsContent value="vouchers" className="space-y-4">
          <Card>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Mã voucher</TableHead>
                  <TableHead>Khách hàng</TableHead>
                  <TableHead>Phần thưởng</TableHead>
                  <TableHead>Trạng thái</TableHead>
                  <TableHead>Ngày cấp</TableHead>
                  <TableHead>Hết hạn</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {mockVouchers.map((v: Voucher) => (
                  <TableRow key={v.id}>
                    <TableCell className="font-mono text-sm">{v.code}</TableCell>
                    <TableCell>{v.customerName}</TableCell>
                    <TableCell>{v.rewardName}</TableCell>
                    <TableCell><Badge className={voucherStatusColors[v.status]}>{v.status}</Badge></TableCell>
                    <TableCell className="text-sm">{v.issuedAt}</TableCell>
                    <TableCell className="text-sm">{v.expiresAt}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Card>
        </TabsContent>
      </Tabs>

      {/* Customer Detail Sheet */}
      <Sheet open={detailOpen} onOpenChange={setDetailOpen}>
        <SheetContent className="sm:max-w-lg overflow-y-auto">
          {selectedCustomer && (
            <>
              <SheetHeader>
                <SheetTitle>{selectedCustomer.name}</SheetTitle>
                <SheetDescription>{selectedCustomer.phone} • {selectedCustomer.memberCode}</SheetDescription>
              </SheetHeader>
              <div className="mt-6 space-y-6">
                <div className="flex gap-3">
                  <Badge className={`${tierColors[selectedCustomer.loyaltyTier]} text-sm px-3 py-1`}>{selectedCustomer.loyaltyTier}</Badge>
                  {selectedCustomer.tags.map(t => <Badge key={t} variant="outline">{t}</Badge>)}
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div className="bg-muted rounded-lg p-3"><p className="text-sm text-muted-foreground">Điểm hiện tại</p><p className="text-xl font-bold">{selectedCustomer.loyaltyPoints.toLocaleString()}</p></div>
                  <div className="bg-muted rounded-lg p-3"><p className="text-sm text-muted-foreground">Tổng chi tiêu</p><p className="text-xl font-bold">{(selectedCustomer.totalSpend / 1000).toLocaleString()}K</p></div>
                  <div className="bg-muted rounded-lg p-3"><p className="text-sm text-muted-foreground">Lượt ghé thăm</p><p className="text-xl font-bold">{selectedCustomer.visitCount}</p></div>
                  <div className="bg-muted rounded-lg p-3"><p className="text-sm text-muted-foreground">TB đơn hàng</p><p className="text-xl font-bold">{(selectedCustomer.averageOrderValue / 1000).toFixed(0)}K</p></div>
                </div>
                {selectedCustomer.notes && <div className="bg-muted rounded-lg p-3"><p className="text-sm text-muted-foreground">Ghi chú</p><p className="text-sm">{selectedCustomer.notes}</p></div>}
                <div>
                  <h4 className="font-semibold mb-3">Lịch sử mua hàng</h4>
                  {customerHistory.length > 0 ? customerHistory.map(h => (
                    <div key={h.id} className="flex items-center justify-between py-2 border-b last:border-0">
                      <div><p className="text-sm font-medium">{h.orderNumber}</p><p className="text-xs text-muted-foreground">{h.date} • {h.outletName}</p><p className="text-xs text-muted-foreground">{h.items.join(', ')}</p></div>
                      <div className="text-right"><p className="font-medium">{(h.total / 1000).toLocaleString()}K</p><p className="text-xs text-green-600">+{h.pointsEarned} pts</p></div>
                    </div>
                  )) : <p className="text-sm text-muted-foreground">Chưa có lịch sử mua hàng</p>}
                </div>
              </div>
            </>
          )}
        </SheetContent>
      </Sheet>
    </div>
  );
}
