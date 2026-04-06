import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Route, Routes, Navigate } from "react-router-dom";
import { Toaster as Sonner } from "@/components/ui/sonner";
import { Toaster } from "@/components/ui/toaster";
import { TooltipProvider } from "@/components/ui/tooltip";
import Login from "./pages/Login";
import ShellLayout from "./layouts/ShellLayout";
import NotFound from "./pages/NotFound";

import DashboardPage from "./pages/DashboardPage";
import POSPage from "./pages/POSPage";
import { InventoryModule } from "@/components/inventory/InventoryModule";
import { ProcurementModule } from "@/components/procurement/ProcurementModule";
import { CatalogModule } from "@/components/catalog/CatalogModule";
import { ReportsModule } from "@/components/reports/ReportsModule";
import { AuditModule } from "@/components/audit/AuditModule";
import { IAMModule } from "@/components/iam/IAMModule";
import { FinanceModule } from "@/components/finance/FinanceModule";
import { HRModule } from "@/components/hr/HRModule";
import { SettingsModule } from "@/components/settings/SettingsModule";
import { CRMModule } from "@/components/crm/CRMModule";
import { PromotionsModule } from "@/components/promotions/PromotionsModule";
import { SchedulingModule } from "@/components/scheduling/SchedulingModule";
import { WorkforceModule } from "@/components/workforce/WorkforceModule";

const queryClient = new QueryClient();


const App = () => (
  <QueryClientProvider client={queryClient}>
    <TooltipProvider>
      <Toaster />
      <Sonner />
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<Login />} />

          <Route element={<ShellLayout />}>
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/pos" element={<POSPage />} />
            <Route path="/inventory" element={<InventoryModule />} />
            <Route path="/procurement" element={<ProcurementModule />} />
            <Route path="/catalog" element={<CatalogModule />} />
            <Route path="/reports" element={<ReportsModule />} />
            <Route path="/audit" element={<AuditModule />} />
            <Route path="/iam" element={<IAMModule />} />
            <Route path="/finance" element={<FinanceModule />} />
            <Route path="/hr" element={<HRModule />} />
            <Route path="/settings" element={<SettingsModule />} />
            <Route path="/crm" element={<CRMModule />} />
            <Route path="/promotions" element={<PromotionsModule />} />
            <Route path="/scheduling" element={<SchedulingModule />} />
            <Route path="/workforce" element={<WorkforceModule />} />
          </Route>

          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="/shell" element={<Navigate to="/dashboard" replace />} />
          <Route path="*" element={<NotFound />} />
        </Routes>
      </BrowserRouter>
    </TooltipProvider>
  </QueryClientProvider>
);

export default App;
