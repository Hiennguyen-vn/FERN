import { useState } from 'react';
import {
  Building2, FileText, Truck, Receipt, CreditCard,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { SupplierModule } from '@/components/procurement/SupplierModule';
import { PurchaseOrderModule } from '@/components/procurement/PurchaseOrderModule';
import { GoodsReceiptModule } from '@/components/procurement/GoodsReceiptModule';
import { InvoiceReviewModule } from '@/components/procurement/InvoiceReviewModule';
import { PaymentReviewModule } from '@/components/procurement/PaymentReviewModule';

type ProcTab = 'suppliers' | 'purchase-orders' | 'goods-receipts' | 'invoices' | 'payments';

const TABS: { key: ProcTab; label: string; icon: React.ElementType }[] = [
  { key: 'suppliers', label: 'Suppliers', icon: Building2 },
  { key: 'purchase-orders', label: 'Purchase Orders', icon: FileText },
  { key: 'goods-receipts', label: 'Goods Receipts', icon: Truck },
  { key: 'invoices', label: 'Invoices', icon: Receipt },
  { key: 'payments', label: 'Payments', icon: CreditCard },
];

export function ProcurementModule() {
  const [activeTab, setActiveTab] = useState<ProcTab>('suppliers');

  return (
    <div className="flex flex-col h-full animate-fade-in">
      <div className="border-b bg-card px-6 flex items-center gap-0 flex-shrink-0">
        {TABS.map(tab => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
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
        {activeTab === 'suppliers' && <SupplierModule />}
        {activeTab === 'purchase-orders' && <PurchaseOrderModule />}
        {activeTab === 'goods-receipts' && <GoodsReceiptModule />}
        {activeTab === 'invoices' && <InvoiceReviewModule />}
        {activeTab === 'payments' && <PaymentReviewModule />}
      </div>
    </div>
  );
}
