import { useState, useMemo } from 'react';
import {
  Search, Monitor, Wifi, WifiOff, User, ShoppingBag, Plus, Minus,
  Trash2, Tag, ArrowLeft, Info, X as XIcon,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import type { ProductItem, OrderLineItem } from '@/types/pos';
import { mockProducts, PRODUCT_CATEGORIES } from '@/data/mock-pos';
import { cn } from '@/lib/utils';
import { TableAssignmentPicker } from '@/components/pos/TableManagement';

interface CartItem extends OrderLineItem {}

interface Props {
  sessionCode: string;
  outletName: string;
  cashierName: string;
  onBack: () => void;
  onCheckout: (items: CartItem[], promo: string | null) => void;
}

export function OrderEntry({ sessionCode, outletName, cashierName, onBack, onCheckout }: Props) {
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('All');
  const [cart, setCart] = useState<CartItem[]>([]);
  const [promoCode, setPromoCode] = useState('');
  const [promoApplied, setPromoApplied] = useState<string | null>(null);
  const [promoDiscount, setPromoDiscount] = useState(0);
  const [tableEnabled] = useState(false); // table integration not yet gateway-available

  const filteredProducts = useMemo(() => {
    return mockProducts.filter(p => {
      if (category !== 'All' && p.category !== category) return false;
      if (search && !p.name.toLowerCase().includes(search.toLowerCase())) return false;
      return true;
    });
  }, [search, category]);

  const addToCart = (product: ProductItem) => {
    if (!product.available) return;
    setCart(prev => {
      const existing = prev.find(i => i.productId === product.id);
      if (existing) {
        return prev.map(i =>
          i.productId === product.id
            ? { ...i, quantity: i.quantity + 1, lineTotal: (i.quantity + 1) * i.unitPrice }
            : i
        );
      }
      return [...prev, {
        id: `li-${Date.now()}`, productId: product.id, productName: product.name,
        category: product.category, quantity: 1, unitPrice: product.price, lineTotal: product.price,
      }];
    });
  };

  const updateQuantity = (productId: string, delta: number) => {
    setCart(prev => prev
      .map(i => i.productId === productId
        ? { ...i, quantity: i.quantity + delta, lineTotal: (i.quantity + delta) * i.unitPrice }
        : i)
      .filter(i => i.quantity > 0)
    );
  };

  const removeItem = (productId: string) => {
    setCart(prev => prev.filter(i => i.productId !== productId));
  };

  const subtotal = cart.reduce((s, i) => s + i.lineTotal, 0);
  const taxRate = 0.08;
  const taxAmount = +(subtotal * taxRate).toFixed(2);
  const adjustedSubtotal = promoApplied ? subtotal - promoDiscount : subtotal;
  const total = +(adjustedSubtotal + taxAmount).toFixed(2);

  const applyPromo = () => {
    if (promoCode.trim().toUpperCase() === 'LUNCH20' && subtotal > 0) {
      const disc = +(subtotal * 0.2).toFixed(2);
      setPromoApplied('LUNCH20');
      setPromoDiscount(disc);
    }
  };

  const clearPromo = () => {
    setPromoApplied(null);
    setPromoDiscount(0);
    setPromoCode('');
  };

  return (
    <div className="flex flex-col h-full animate-fade-in">
      {/* Order header */}
      <div className="px-4 py-2.5 border-b bg-card flex items-center gap-4 flex-shrink-0">
        <button onClick={onBack} className="text-muted-foreground hover:text-foreground transition-colors">
          <ArrowLeft className="h-4 w-4" />
        </button>
        <div className="flex items-center gap-4 flex-1 min-w-0">
          <div className="flex items-center gap-1.5">
            <Monitor className="h-3.5 w-3.5 text-muted-foreground" />
            <span className="text-xs text-foreground font-medium">{sessionCode}</span>
          </div>
          <span className="text-xs text-muted-foreground">{outletName}</span>
          <div className="flex items-center gap-1.5">
            <User className="h-3 w-3 text-muted-foreground" />
            <span className="text-xs text-muted-foreground">{cashierName}</span>
          </div>
        </div>
        <div className="flex items-center gap-1.5">
          <Wifi className="h-3.5 w-3.5 text-success" />
          <span className="text-[10px] font-medium text-success">Online</span>
        </div>
      </div>

      <div className="flex flex-1 min-h-0">
        {/* LEFT — Product catalog */}
        <div className="flex-1 flex flex-col min-w-0 border-r">
          {/* Search + categories */}
          <div className="p-3 border-b space-y-2.5">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
              <Input
                placeholder="Search products…"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="pl-9 h-8 text-sm"
              />
            </div>
            <div className="flex items-center gap-1.5 overflow-x-auto pb-0.5">
              {PRODUCT_CATEGORIES.map((cat) => (
                <button
                  key={cat}
                  onClick={() => setCategory(cat)}
                  className={cn(
                    'text-[11px] px-2.5 py-1.5 rounded-md border whitespace-nowrap transition-colors',
                    category === cat
                      ? 'bg-primary text-primary-foreground border-primary'
                      : 'bg-card text-foreground hover:bg-accent border-border'
                  )}
                >
                  {cat}
                </button>
              ))}
            </div>
          </div>

          {/* Product grid */}
          <div className="flex-1 overflow-y-auto p-3">
            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-2">
              {filteredProducts.map((product) => (
                <button
                  key={product.id}
                  onClick={() => addToCart(product)}
                  disabled={!product.available}
                  className={cn(
                    'p-3 rounded-lg border text-left transition-all',
                    product.available
                      ? 'hover:border-primary/30 hover:shadow-surface-sm bg-card cursor-pointer'
                      : 'opacity-40 cursor-not-allowed bg-muted/30'
                  )}
                >
                  <p className="text-xs font-medium text-foreground leading-tight">{product.name}</p>
                  <p className="text-[10px] text-muted-foreground mt-0.5">{product.category}</p>
                  <div className="flex items-center justify-between mt-2">
                    <span className="text-sm font-semibold text-foreground">${product.price.toFixed(2)}</span>
                    {!product.available && <span className="text-[9px] text-destructive font-medium">Unavailable</span>}
                  </div>
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* RIGHT — Cart / Order summary */}
        <div className="w-[320px] flex flex-col bg-card flex-shrink-0">
          <div className="px-4 py-3 border-b">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-semibold text-foreground flex items-center gap-1.5">
                <ShoppingBag className="h-4 w-4" /> Current Order
              </h3>
              <span className="text-[10px] text-muted-foreground">{cart.length} items</span>
            </div>
          </div>

          {/* Cart items */}
          <div className="flex-1 overflow-y-auto">
            {cart.length === 0 ? (
              <div className="flex flex-col items-center justify-center h-full text-center p-4">
                <ShoppingBag className="h-8 w-8 text-muted-foreground/30 mb-2" />
                <p className="text-xs text-muted-foreground">No items added</p>
                <p className="text-[10px] text-muted-foreground mt-0.5">Tap a product to add it to the order</p>
              </div>
            ) : (
              <div className="p-3 space-y-2">
                {cart.map((item) => (
                  <div key={item.productId} className="flex items-start gap-2 p-2.5 rounded-md bg-muted/30">
                    <div className="flex-1 min-w-0">
                      <p className="text-xs font-medium text-foreground">{item.productName}</p>
                      <p className="text-[10px] text-muted-foreground">${item.unitPrice.toFixed(2)} each</p>
                    </div>
                    <div className="flex items-center gap-1.5">
                      <button onClick={() => updateQuantity(item.productId, -1)} className="h-6 w-6 rounded border flex items-center justify-center hover:bg-accent transition-colors text-foreground">
                        <Minus className="h-3 w-3" />
                      </button>
                      <span className="text-xs font-medium w-5 text-center text-foreground">{item.quantity}</span>
                      <button onClick={() => updateQuantity(item.productId, 1)} className="h-6 w-6 rounded border flex items-center justify-center hover:bg-accent transition-colors text-foreground">
                        <Plus className="h-3 w-3" />
                      </button>
                    </div>
                    <div className="text-right min-w-[50px]">
                      <p className="text-xs font-semibold text-foreground">${item.lineTotal.toFixed(2)}</p>
                      <button onClick={() => removeItem(item.productId)} className="text-destructive hover:text-destructive/80 transition-colors">
                        <Trash2 className="h-3 w-3" />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Promo code */}
          <div className="px-3 py-2 border-t">
            {promoApplied ? (
              <div className="flex items-center justify-between p-2 rounded-md bg-success/5 border border-success/15">
                <div className="flex items-center gap-1.5">
                  <Tag className="h-3 w-3 text-success" />
                  <span className="text-[11px] font-medium text-success">{promoApplied}</span>
                </div>
                <div className="flex items-center gap-2">
                  <span className="text-[11px] font-medium text-success">−${promoDiscount.toFixed(2)}</span>
                  <button onClick={clearPromo} className="text-muted-foreground hover:text-foreground"><XIcon className="h-3 w-3" /></button>
                </div>
              </div>
            ) : (
              <div className="flex gap-1.5">
                <Input
                  placeholder="Promo code"
                  value={promoCode}
                  onChange={(e) => setPromoCode(e.target.value)}
                  className="h-7 text-xs flex-1"
                />
                <Button variant="outline" size="sm" className="h-7 text-[10px] px-2" onClick={applyPromo}>Apply</Button>
              </div>
            )}
          </div>

          {/* Table assignment */}
          <div className="px-3 py-2 border-t">
            <TableAssignmentPicker
              onSelect={(tableId) => { /* would set tableId on order */ }}
              gatewayAvailable={false}
              permissionsBootstrapped={false}
            />
          </div>

          {/* Order totals + checkout */}
          <div className="px-3 py-3 border-t bg-muted/20 space-y-2">
            <div className="flex justify-between text-xs text-muted-foreground">
              <span>Subtotal</span>
              <span>${subtotal.toFixed(2)}</span>
            </div>
            {promoApplied && (
              <div className="flex justify-between text-xs text-success">
                <span>Discount ({promoApplied})</span>
                <span>−${promoDiscount.toFixed(2)}</span>
              </div>
            )}
            <div className="flex justify-between text-xs text-muted-foreground">
              <span>Tax (8%)</span>
              <span>${taxAmount.toFixed(2)}</span>
            </div>
            <div className="flex justify-between text-sm font-semibold text-foreground pt-1 border-t">
              <span>Total</span>
              <span>${total > 0 ? total.toFixed(2) : '0.00'}</span>
            </div>
            <Button
              className="w-full h-9 text-xs mt-2"
              disabled={cart.length === 0}
              onClick={() => onCheckout(cart, promoApplied)}
            >
              Proceed to Payment — ${total > 0 ? total.toFixed(2) : '0.00'}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
