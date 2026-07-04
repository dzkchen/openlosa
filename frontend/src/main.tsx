import React from "react";
import ReactDOM from "react-dom/client";
import { Navigate, Route, RouterProvider, createBrowserRouter, createRoutesFromElements } from "react-router-dom";
import App from "./App";
import ApplicationsPage from "./features/applications/ApplicationsPage";
import ContactsPage from "./features/contacts/ContactsPage";
import FavoritesPage from "./features/favorites/FavoritesPage";
import FeedPage from "./features/feed/FeedPage";
import OutreachPage from "./features/outreach/OutreachPage";
import ProspectsPage from "./features/prospects/ProspectsPage";
import SummaryPage from "./features/summary/SummaryPage";
import "./index.css";

const router = createBrowserRouter(
  createRoutesFromElements(
    <Route path="/" element={<App />}>
      <Route index element={<Navigate to="/summary" replace />} />
      <Route path="summary" element={<SummaryPage />} />
      <Route path="applications" element={<ApplicationsPage />} />
      <Route path="prospects" element={<ProspectsPage />} />
      <Route path="contacts" element={<ContactsPage />} />
      <Route path="outreach" element={<OutreachPage />} />
      <Route path="feed" element={<FeedPage />} />
      <Route path="favorites" element={<FavoritesPage />} />
    </Route>
  )
);

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <RouterProvider router={router} />
  </React.StrictMode>
);
