import { useState } from 'react';
import {
  Package, ScrollText, ClipboardCheck, ArrowLeftRight, Trash2,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { StockBalanceOverview } from '@/components/inventory/StockBalanceOverview';
import { InventoryLedger } from '@/components/inventory/InventoryLedger';
import { StockCountModule } from '@/components/inventory/StockCountModule';
import { StockAdjustmentModule } from '@/components/inventory/StockAdjustment';
import { WasteRecordModule } from '@/components/inventory/WasteRecordModule';

type InventoryTab = 'balances' | 'ledger' | 'counts' | 'adjustments' | 'waste';

const TABS: { key: InventoryTab; label: string; icon: React.ElementType }[] = [
  { key: 'balances', label: 'Stock Balances', icon: Package },
  { key: 'ledger', label: 'Ledger', icon: ScrollText },
  { key: 'counts', label: 'Stock Counts', icon: ClipboardCheck },
  { key: 'adjustments', label: 'Adjustments', icon: ArrowLeftRight },
  { key: 'waste', label: 'Waste', icon: Trash2 },
];

export function InventoryModule() {
  const [activeTab, setActiveTab] = useState<InventoryTab>('balances');

  return (
    <div className="flex flex-col h-full animate-fade-in">
      {/* Tab bar */}
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

      {/* Tab content */}
      <div className="flex-1 overflow-y-auto">
        {activeTab === 'balances' && <StockBalanceOverview />}
        {activeTab === 'ledger' && <InventoryLedger />}
        {activeTab === 'counts' && <StockCountModule />}
        {activeTab === 'adjustments' && <StockAdjustmentModule />}
        {activeTab === 'waste' && <WasteRecordModule />}
      </div>
    </div>
  );
}
