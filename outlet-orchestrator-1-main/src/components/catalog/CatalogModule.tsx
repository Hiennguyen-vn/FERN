import { useState } from 'react';
import {
  Package, Leaf, BookOpen, DollarSign, Tag,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { CatalogHome } from '@/components/catalog/CatalogHome';
import { ProductMaster } from '@/components/catalog/ProductMaster';
import { IngredientModule } from '@/components/catalog/IngredientModule';
import { RecipeModule } from '@/components/catalog/RecipeModule';
import { PricingModule } from '@/components/catalog/PricingModule';

type CatTab = 'home' | 'products' | 'ingredients' | 'recipes' | 'pricing';

const TABS: { key: CatTab; label: string; icon: React.ElementType }[] = [
  { key: 'home', label: 'Overview', icon: Package },
  { key: 'products', label: 'Products', icon: Package },
  { key: 'ingredients', label: 'Ingredients & UoM', icon: Leaf },
  { key: 'recipes', label: 'Recipes', icon: BookOpen },
  { key: 'pricing', label: 'Pricing & Promos', icon: DollarSign },
];

export function CatalogModule() {
  const [activeTab, setActiveTab] = useState<CatTab>('home');

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
        {activeTab === 'home' && <CatalogHome onNavigate={setActiveTab} />}
        {activeTab === 'products' && <ProductMaster />}
        {activeTab === 'ingredients' && <IngredientModule />}
        {activeTab === 'recipes' && <RecipeModule />}
        {activeTab === 'pricing' && <PricingModule />}
      </div>
    </div>
  );
}
